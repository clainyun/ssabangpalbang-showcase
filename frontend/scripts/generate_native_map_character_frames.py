"""Mapbox SymbolLayer용 개별 캐릭터 프레임을 생성하고 검증한다."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image


CELL_SIZE = 256
BOTTOM_PADDING = 16
OUTPUT_HEIGHT = CELL_SIZE - BOTTOM_PADDING

SCRIPT_DIR = Path(__file__).resolve().parent
CHARACTER_DIR = SCRIPT_DIR.parent / "assets" / "images" / "characters"
OUTPUT_DIR = CHARACTER_DIR / "native-map"
SPRITE_CONFIG_PATH = (
    SCRIPT_DIR.parent / "src" / "features" / "character" / "nativeMapSpriteConfig.ts"
)

WALK_DIRECTIONS = ("n", "ne", "e", "se", "s", "sw", "w", "nw")
IDLE_VARIANTS = (1, 2, 3)

# (원본 폴더, processed 접두사) — scripts/process_palbang_sprites.py 가 만든
# palbang_*/processed/ 시트. 8방향 전부 실물이라 좌우 반전이 필요 없다.
CHARACTERS = (
    ("palbang_plain", "plain"),
    ("palbang_dog", "dog"),
    ("palbang_rabbit", "rabbit"),
)


@dataclass(frozen=True)
class SheetJob:
    source: Path
    output_prefix: str
    frame_count: int


def _build_sheet_jobs() -> tuple[SheetJob, ...]:
    jobs: list[SheetJob] = []
    for folder, prefix in CHARACTERS:
        processed_dir = CHARACTER_DIR / folder / "processed"
        for direction in WALK_DIRECTIONS:
            name = f"{prefix}_walk_{direction}"
            jobs.append(SheetJob(processed_dir / f"{name}_processed.png", name, 6))
        for variant in IDLE_VARIANTS:
            name = f"{prefix}_idle_s_{variant}"
            jobs.append(SheetJob(processed_dir / f"{name}_processed.png", name, 6))
    return tuple(jobs)


SHEET_JOBS = _build_sheet_jobs()


def output_path(prefix: str, frame_index: int, output_dir: Path = OUTPUT_DIR) -> Path:
    return output_dir / f"{prefix}_{frame_index}.png"


def expected_paths(output_dir: Path = OUTPUT_DIR) -> set[Path]:
    paths: set[Path] = set()
    for job in SHEET_JOBS:
        for frame_index in range(job.frame_count):
            paths.add(output_path(job.output_prefix, frame_index, output_dir))
    return paths


def split_sheet(job: SheetJob, output_dir: Path) -> None:
    sheet = Image.open(job.source).convert("RGBA")
    expected_size = (CELL_SIZE * job.frame_count, CELL_SIZE)
    if sheet.size != expected_size:
        raise ValueError(f"{job.source} size={sheet.size}, expected={expected_size}")
    if sheet.getchannel("A").crop((0, OUTPUT_HEIGHT, sheet.width, CELL_SIZE)).getbbox():
        raise ValueError(f"{job.source} has visible pixels in the cropped bottom padding")

    for frame_index in range(job.frame_count):
        left = frame_index * CELL_SIZE
        frame = sheet.crop((left, 0, left + CELL_SIZE, OUTPUT_HEIGHT))
        frame.save(output_path(job.output_prefix, frame_index, output_dir))


def generate() -> None:
    OUTPUT_DIR.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix="native-map-", dir=OUTPUT_DIR.parent) as temporary_dir:
        generated_dir = Path(temporary_dir)
        for job in SHEET_JOBS:
            split_sheet(job, generated_dir)
        check(generated_dir, check_manifest=False)

        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        for stale_path in OUTPUT_DIR.glob("*.png"):
            stale_path.unlink()
        for generated_path in generated_dir.glob("*.png"):
            generated_path.replace(OUTPUT_DIR / generated_path.name)


def check(output_dir: Path = OUTPUT_DIR, *, check_manifest: bool = True) -> None:
    expected = expected_paths(output_dir)
    actual = set(output_dir.glob("*.png")) if output_dir.exists() else set()
    if actual != expected:
        missing = sorted(path.name for path in expected - actual)
        unexpected = sorted(path.name for path in actual - expected)
        raise ValueError(f"generated frame set mismatch: missing={missing}, unexpected={unexpected}")

    for path in sorted(expected):
        with Image.open(path) as frame:
            if frame.mode != "RGBA":
                raise ValueError(f"{path.name} mode={frame.mode}, expected=RGBA")
            if frame.size != (CELL_SIZE, OUTPUT_HEIGHT):
                raise ValueError(
                    f"{path.name} size={frame.size}, expected={(CELL_SIZE, OUTPUT_HEIGHT)}"
                )
            if frame.getbbox() is None:
                raise ValueError(f"{path.name} has no visible pixels")
            if frame.getbbox()[3] != OUTPUT_HEIGHT:
                raise ValueError(f"{path.name} does not place visible pixels at the bottom anchor")

    for job in SHEET_JOBS:
        sheet = Image.open(job.source).convert("RGBA")
        expected_size = (CELL_SIZE * job.frame_count, CELL_SIZE)
        if sheet.size != expected_size:
            raise ValueError(f"{job.source} size={sheet.size}, expected={expected_size}")
        if sheet.getchannel("A").crop((0, OUTPUT_HEIGHT, sheet.width, CELL_SIZE)).getbbox():
            raise ValueError(f"{job.source} has visible pixels in the cropped bottom padding")

        for frame_index in range(job.frame_count):
            left = frame_index * CELL_SIZE
            source_frame = sheet.crop((left, 0, left + CELL_SIZE, OUTPUT_HEIGHT))
            path = output_path(job.output_prefix, frame_index, output_dir)
            with Image.open(path) as actual_frame:
                if actual_frame.convert("RGBA").tobytes() != source_frame.tobytes():
                    raise ValueError(f"{path.name} is stale or differs from its source")

    if check_manifest:
        registered_names = re.findall(
            r"native-map/([^']+\.png)", SPRITE_CONFIG_PATH.read_text(encoding="utf-8")
        )
        expected_names = {path.name for path in expected}
        if len(registered_names) != len(set(registered_names)):
            raise ValueError("native map sprite manifest contains duplicate image requires")
        if set(registered_names) != expected_names:
            missing = sorted(expected_names - set(registered_names))
            unexpected = sorted(set(registered_names) - expected_names)
            raise ValueError(
                f"sprite manifest mismatch: missing={missing}, unexpected={unexpected}"
            )

    print(f"validated {len(expected)} native map character frames")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="생성 결과만 검증한다")
    args = parser.parse_args()

    if not args.check:
        generate()
    check()


if __name__ == "__main__":
    main()
