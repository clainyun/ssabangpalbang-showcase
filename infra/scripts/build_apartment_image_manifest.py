#!/usr/bin/env python3
"""Build the canonical apartment-image mapping, Flyway seed, and upload tree."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path


DISTRICTS = {
    "dobong": "도봉구",
    "dongdaemun": "동대문구",
    "dongjak": "동작구",
    "eunpyeong": "은평구",
    "gangbuk": "강북구",
    "gangdong": "강동구",
    "gangnam": "강남구",
    "gangseo": "강서구",
    "geumcheon": "금천구",
    "guro": "구로구",
    "gwanak": "관악구",
    "gwangjin": "광진구",
    "jongno": "종로구",
    "jung-gu": "중구",
    "jungnang": "중랑구",
    "mapo": "마포구",
    "nowon": "노원구",
    "seocho": "서초구",
    "seodaemun": "서대문구",
    "seongbuk": "성북구",
    "seongdong": "성동구",
    "songpa": "송파구",
    "yangcheon": "양천구",
    "yeongdeungpo": "영등포구",
    "yongsan": "용산구",
}

MANIFEST_COLUMNS = [
    "apartment_id",
    "complex_code",
    "db_name",
    "district_name",
    "dong_name",
    "household_count",
    "source_slug",
    "source_name",
    "source_legal_dong",
    "source_households",
    "object_key",
    "source_webp",
    "source_csv",
    "sha256",
    "match_method",
    "match_confidence",
    "review_status",
    "candidate_complex_codes",
    "notes",
]

# 옥수동 전용 제작물은 성동구 일괄 CSV와 별도 manifest로 관리된다. DB가 임대동을
# 별도 complex_code로 나누거나 공식 명칭을 다르게 보관한 경우만 검증된 별칭으로
# 명시한다. 소스가 없는 단지에 비슷한 이미지를 임의로 붙이지 않는다.
OKSU_SOURCE_BY_COMPLEX_CODE = {
    "A10026748": "e-pyeonhansesang-oksu-parkhills",
    "A13310004": "oksu-kukdong",
    "A13375901": "hannam-heights",
    "A13375902": "oksu-samsung",
    "A13375903": "oksu-riverside-poonglim-iwon",
    "A13375905": "oksu-samsung",
    "A13375906": "oksu-eoullim",
    "A13375907": "raemian-oksu-riverzen",
    "A13375908": "raemian-oksu-riverzen",
    "A13376702": "oksu-hyundai",
    "A13383801": "oksu-heights",
    "A13384403": "oksu-kukdong-green",
}

OKSU_SHARED_COMPLEX_CODES = {"A13375905", "A13375908"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets-root", required=True, type=Path)
    parser.add_argument("--apartments-csv", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--migration-out", type=Path)
    parser.add_argument("--oksu-manifest", type=Path)
    parser.add_argument("--version", default="v1")
    parser.add_argument("--stage-files", action="store_true")
    return parser.parse_args()


def normalized(value: str | None) -> str:
    text = unicodedata.normalize("NFKC", value or "").casefold()
    return re.sub(r"[^0-9a-z가-힣]", "", text)


def integer(value: str | None) -> int | None:
    if value is None or not value.strip():
        return None
    try:
        return int(value.replace(",", "").strip())
    except ValueError:
        return None


def selected_indexes(assets_root: Path) -> list[tuple[str, str, Path]]:
    found: dict[str, dict[str, Path]] = defaultdict(dict)
    pattern = re.compile(r"^(?P<district>.+)-apartments-(?P<variant>hybrid-v2|quality-v3)$")
    for index_path in assets_root.glob("*/apartment-index.csv"):
        match = pattern.match(index_path.parent.name)
        if match and match.group("district") in DISTRICTS:
            found[match.group("district")][match.group("variant")] = index_path

    missing = sorted(set(DISTRICTS) - set(found))
    if missing:
        raise RuntimeError(f"Missing district indexes: {', '.join(missing)}")

    result = []
    for slug in DISTRICTS:
        variant = "quality-v3" if "quality-v3" in found[slug] else "hybrid-v2"
        result.append((slug, variant, found[slug][variant]))
    return result


def load_apartments(path: Path) -> list[dict[str, object]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source))
    for row in rows:
        row["id"] = int(row["id"])
        row["household_count"] = integer(row.get("household_count"))
        row["name_key"] = normalized(row.get("name"))
        row["district_key"] = normalized(row.get("district_name"))
        row["dong_key"] = normalized(row.get("dong_name"))
    return rows


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def source_path(repo_root: Path, assets_root: Path, raw_path: str) -> Path:
    candidate = (repo_root / raw_path).resolve()
    if candidate.is_file():
        return candidate
    candidate = (assets_root / Path(raw_path).name).resolve()
    if candidate.is_file():
        return candidate
    raise FileNotFoundError(raw_path)


def narrowed_candidates(
    candidates: list[dict[str, object]],
    district: str,
    dong: str,
    households: int | None,
) -> list[dict[str, object]]:
    working = candidates
    for predicate in (
        lambda item: item["district_key"] == normalized(district),
        lambda item: item["dong_key"] == normalized(dong),
        lambda item: households is not None and item["household_count"] == households,
    ):
        preferred = [item for item in working if predicate(item)]
        if preferred:
            working = preferred
    return working


def match_method(apartment: dict[str, object], district: str, dong: str, households: int | None) -> str:
    parts = ["name"]
    if apartment["district_key"] == normalized(district):
        parts.append("district")
    if apartment["dong_key"] == normalized(dong):
        parts.append("dong")
    if households is not None and apartment["household_count"] == households:
        parts.append("households")
    return "+".join(parts)


def confidence(method: str) -> str:
    if method in {"name+district+dong+households", "name+district+dong", "name+dong"}:
        return "HIGH"
    if "households" in method or "district" in method:
        return "MEDIUM"
    return "LOW"


def sql_literal(value: object) -> str:
    return "'" + str(value).replace("'", "''") + "'"


def write_migration(path: Path, matched: list[dict[str, object]]) -> None:
    values = []
    for row in matched:
        values.append(
            "        ("
            + ", ".join(
                sql_literal(row[column])
                for column in (
                    "complex_code",
                    "object_key",
                    "sha256",
                    "source_slug",
                    "source_csv",
                    "match_method",
                    "match_confidence",
                )
            )
            + ")"
        )

    sql = """-- BE-004: map verified public apartment illustrations to canonical apartment rows.
CREATE TABLE apartment_image (
    apartment_id      BIGINT PRIMARY KEY REFERENCES apartment(id),
    object_key        VARCHAR(255) UNIQUE NOT NULL,
    sha256            CHAR(64) NOT NULL,
    source_slug       VARCHAR(100) NOT NULL,
    source_csv        VARCHAR(500) NOT NULL,
    match_method      VARCHAR(64) NOT NULL,
    match_confidence  VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_apartment_image_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_apartment_image_match_confidence
        CHECK (match_confidence IN ('HIGH', 'MEDIUM', 'LOW'))
);

COMMENT ON TABLE apartment_image IS
    '아파트 대표 일러스트의 공개 object key와 검증 가능한 매칭 메타데이터';
COMMENT ON COLUMN apartment_image.object_key IS
    '정적 이미지 호스트의 base URL 뒤에 결합할 상대 object key';

WITH image_source (
    complex_code,
    object_key,
    sha256,
    source_slug,
    source_csv,
    match_method,
    match_confidence
) AS (
    VALUES
""" + ",\n".join(values) + """
)
INSERT INTO apartment_image (
    apartment_id,
    object_key,
    sha256,
    source_slug,
    source_csv,
    match_method,
    match_confidence
)
SELECT
    apartment.id,
    image_source.object_key,
    image_source.sha256,
    image_source.source_slug,
    image_source.source_csv,
    image_source.match_method,
    image_source.match_confidence
FROM image_source
JOIN apartment ON apartment.complex_code = image_source.complex_code;
"""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(sql, encoding="utf-8", newline="\n")


def stage_image(webp: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        return
    try:
        os.link(webp, destination)
    except OSError:
        shutil.copy2(webp, destination)


def load_oksu_matches(
    manifest_path: Path,
    repo_root: Path,
    apartments: list[dict[str, object]],
    output_dir: Path,
    version: str,
    stage_files: bool,
) -> list[dict[str, object]]:
    raw = json.loads(manifest_path.read_text(encoding="utf-8"))
    source_by_slug = {item["slug"]: item for item in raw["complexes"]}
    apartment_by_code = {str(item["complex_code"]): item for item in apartments}
    relative_manifest = manifest_path.relative_to(repo_root).as_posix()
    matched = []

    missing_sources = sorted(set(OKSU_SOURCE_BY_COMPLEX_CODE.values()) - set(source_by_slug))
    missing_apartments = sorted(set(OKSU_SOURCE_BY_COMPLEX_CODE) - set(apartment_by_code))
    if missing_sources or missing_apartments:
        raise RuntimeError(
            "Invalid Oksu mapping: "
            f"missing sources={missing_sources}, missing apartments={missing_apartments}"
        )

    for complex_code, source_slug in OKSU_SOURCE_BY_COMPLEX_CODE.items():
        apartment = apartment_by_code[complex_code]
        source = source_by_slug[source_slug]
        webp = (manifest_path.parent / source["appOutput"]).resolve()
        if not webp.is_file():
            raise FileNotFoundError(webp)

        if complex_code in OKSU_SHARED_COMPLEX_CODES:
            method = "verified-oksu-shared-complex"
            notes = "verified rental split shares the parent complex layout"
        elif normalized(str(apartment["name"])) == normalized(str(source["name"])):
            method = "name+district+dong+households"
            notes = "selected separate Oksu manifest"
        else:
            method = "verified-oksu-alias"
            notes = "verified official-name alias in separate Oksu manifest"

        row = {
            "apartment_id": apartment["id"],
            "complex_code": complex_code,
            "db_name": apartment["name"],
            "district_name": apartment["district_name"],
            "dong_name": apartment["dong_name"],
            "household_count": apartment["household_count"] or "",
            "source_slug": source_slug,
            "source_name": source["name"],
            "source_legal_dong": "옥수동",
            "source_households": source.get("households") or "",
            "object_key": f"apartment-images/{version}/{complex_code}.webp",
            "source_webp": webp.relative_to(repo_root).as_posix(),
            "source_csv": relative_manifest,
            "sha256": sha256(webp),
            "match_method": method,
            "match_confidence": "HIGH",
            "review_status": "AUTO_APPROVED",
            "candidate_complex_codes": complex_code,
            "notes": notes,
        }
        matched.append(row)
        if stage_files:
            stage_image(webp, output_dir / "upload" / version / f"{complex_code}.webp")

    return matched


def main() -> None:
    args = parse_args()
    assets_root = args.assets_root.resolve()
    repo_root = assets_root.parents[2]
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    apartments = load_apartments(args.apartments_csv.resolve())
    by_name: dict[str, list[dict[str, object]]] = defaultdict(list)
    for apartment in apartments:
        by_name[str(apartment["name_key"])].append(apartment)

    manifest: list[dict[str, object]] = []
    selected = selected_indexes(assets_root)
    for district_slug, variant, index_path in selected:
        district_name = DISTRICTS[district_slug]
        relative_index = index_path.relative_to(repo_root).as_posix()
        with index_path.open(encoding="utf-8-sig", newline="") as source:
            for source_row in csv.DictReader(source):
                source_households = integer(source_row.get("households"))
                candidates = by_name.get(normalized(source_row.get("name")), [])
                candidates = narrowed_candidates(
                    candidates,
                    district_name,
                    source_row.get("legalDong", ""),
                    source_households,
                )
                candidate_codes = ";".join(sorted(str(item["complex_code"]) for item in candidates))
                result = {
                    "apartment_id": "",
                    "complex_code": "",
                    "db_name": "",
                    "district_name": district_name,
                    "dong_name": "",
                    "household_count": "",
                    "source_slug": source_row.get("slug", ""),
                    "source_name": source_row.get("name", ""),
                    "source_legal_dong": source_row.get("legalDong", ""),
                    "source_households": source_households or "",
                    "object_key": "",
                    "source_webp": source_row.get("appOutput", ""),
                    "source_csv": relative_index,
                    "sha256": "",
                    "match_method": "",
                    "match_confidence": "",
                    "review_status": "",
                    "candidate_complex_codes": candidate_codes,
                    "notes": f"selected {variant}",
                }
                if len(candidates) == 1:
                    apartment = candidates[0]
                    method = match_method(
                        apartment,
                        district_name,
                        source_row.get("legalDong", ""),
                        source_households,
                    )
                    webp = source_path(repo_root, assets_root, source_row["appOutput"])
                    result.update(
                        {
                            "apartment_id": apartment["id"],
                            "complex_code": apartment["complex_code"],
                            "db_name": apartment["name"],
                            "district_name": apartment["district_name"],
                            "dong_name": apartment["dong_name"],
                            "household_count": apartment["household_count"] or "",
                            "object_key": f"apartment-images/{args.version}/{apartment['complex_code']}.webp",
                            "sha256": sha256(webp),
                            "match_method": method,
                            "match_confidence": confidence(method),
                            "review_status": "AUTO_APPROVED",
                        }
                    )
                    if args.stage_files:
                        destination = output_dir / "upload" / args.version / f"{apartment['complex_code']}.webp"
                        stage_image(webp, destination)
                elif candidates:
                    result["review_status"] = "REVIEW_REQUIRED"
                    result["notes"] = f"ambiguous after normalization; selected {variant}"
                else:
                    result["review_status"] = "UNMATCHED"
                    result["notes"] = f"no normalized-name candidate; selected {variant}"
                manifest.append(result)

    base_matched = [row for row in manifest if row["review_status"] == "AUTO_APPROVED"]
    oksu_matched: list[dict[str, object]] = []
    if args.oksu_manifest:
        oksu_matched = load_oksu_matches(
            args.oksu_manifest.resolve(),
            repo_root,
            apartments,
            output_dir,
            args.version,
            args.stage_files,
        )
        manifest.extend(oksu_matched)

    matched = base_matched + oksu_matched
    duplicate_codes = [
        code for code, count in Counter(str(row["complex_code"]) for row in matched).items() if count > 1
    ]
    if duplicate_codes:
        raise RuntimeError(f"Duplicate apartment matches: {', '.join(duplicate_codes)}")

    manifest_path = output_dir / "apartment-image-mapping.csv"
    with manifest_path.open("w", encoding="utf-8-sig", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=MANIFEST_COLUMNS)
        writer.writeheader()
        writer.writerows(manifest)

    summary = {
        "sourceIndexCount": len(selected),
        "sourceRowCount": len(manifest),
        "supplementalOksuCount": len(oksu_matched),
        "matchedCount": len(matched),
        "unmatchedCount": sum(row["review_status"] == "UNMATCHED" for row in manifest),
        "reviewRequiredCount": sum(row["review_status"] == "REVIEW_REQUIRED" for row in manifest),
        "databaseApartmentCount": len(apartments),
        "databaseFallbackCount": len(apartments) - len(matched),
        "matchMethods": dict(Counter(str(row["match_method"]) for row in matched)),
        "selectedIndexes": [
            {
                "district": DISTRICTS[slug],
                "variant": variant,
                "sourceCsv": path.relative_to(repo_root).as_posix(),
            }
            for slug, variant, path in selected
        ]
        + (
            [
                {
                    "district": "성동구 옥수동",
                    "variant": "curated-oksu-v1",
                    "sourceCsv": args.oksu_manifest.resolve().relative_to(repo_root).as_posix(),
                }
            ]
            if args.oksu_manifest
            else []
        ),
    }
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    if args.migration_out:
        write_migration(args.migration_out.resolve(), matched)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
