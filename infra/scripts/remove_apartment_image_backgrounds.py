#!/usr/bin/env python3
"""Remove the edge-connected studio background from apartment WebP assets."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
from concurrent.futures import ProcessPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


PAGE_BACKGROUND = (231, 235, 230)


@dataclass(frozen=True)
class ImageResult:
    complex_code: str
    input_path: str
    output_path: str
    width: int
    height: int
    input_sha256: str
    output_sha256: str
    background_rgb: str
    soft_distance: float
    hard_distance: float
    transparent_ratio: float
    partial_ratio: float
    opaque_ratio: float
    subject_bbox: str
    status: str
    note: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--manifest-out", required=True, type=Path)
    parser.add_argument("--migration-out", type=Path)
    parser.add_argument("--preview-out", type=Path)
    parser.add_argument("--review-preview-out", type=Path)
    parser.add_argument("--version", default="v2")
    parser.add_argument("--workers", type=int, default=max(1, min(8, (os.cpu_count() or 2) - 1)))
    parser.add_argument("--limit", type=int)
    parser.add_argument("--only", nargs="*")
    parser.add_argument("--resume", action="store_true")
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def background_model(rgb: np.ndarray) -> tuple[np.ndarray, np.ndarray, float, float]:
    height, width, _ = rgb.shape
    sample_height = max(8, height // 18)
    sample_width = max(8, width // 18)
    regions = [
        rgb[:sample_height, :sample_width],
        rgb[:sample_height, -sample_width:],
        rgb[-sample_height:, :sample_width],
        rgb[-sample_height:, -sample_width:],
    ]
    corner_colors = np.stack(
        [np.median(region.reshape(-1, 3), axis=0) for region in regions], axis=0
    ).astype(np.float32)
    variation = np.concatenate(
        [
            np.linalg.norm(region.reshape(-1, 3) - color, axis=1)
            for region, color in zip(regions, corner_colors, strict=True)
        ]
    )
    clean_variation = variation[variation <= np.quantile(variation, 0.90)]
    background_variation = float(np.quantile(clean_variation, 0.95))
    soft_distance = max(4.0, min(14.0, background_variation + 1.5))
    hard_distance = max(72.0, min(84.0, soft_distance + 70.0))

    return np.mean(corner_colors, axis=0), corner_colors, soft_distance, hard_distance


def nearest_background(rgb: np.ndarray, palette: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    distance = np.full(rgb.shape[:2], np.inf, dtype=np.float32)
    background_field = np.empty_like(rgb)
    for color in palette:
        candidate_distance = np.linalg.norm(rgb - color, axis=2)
        nearer = candidate_distance < distance
        distance[nearer] = candidate_distance[nearer]
        background_field[nearer] = color
    return distance, background_field


def connected_background(candidate: np.ndarray) -> np.ndarray:
    # Pillow's flood fill is memory-efficient for the binary, edge-connected mask and
    # avoids introducing an OpenCV/scipy runtime dependency into the repository tool.
    mask = Image.fromarray(np.where(candidate, 0, 255).astype(np.uint8)).copy()
    width, height = mask.size
    step = max(16, min(width, height) // 32)
    seeds = []
    for x in range(0, width, step):
        seeds.extend(((x, 0), (x, height - 1)))
    for y in range(0, height, step):
        seeds.extend(((0, y), (width - 1, y)))
    seeds.extend(((width - 1, 0), (0, height - 1), (width - 1, height - 1)))
    pixels = mask.load()
    for seed in seeds:
        if pixels[seed] == 0:
            ImageDraw.floodfill(mask, seed, 128, thresh=0)
    return np.asarray(mask) == 128


def remove_background(source_path: Path, destination_path: Path) -> ImageResult:
    with Image.open(source_path) as opened:
        image = opened.convert("RGB")
    rgb = np.asarray(image, dtype=np.float32)
    background, background_palette, soft_distance, hard_distance = background_model(rgb)
    distance, background_field = nearest_background(rgb, background_palette)
    connected = connected_background(distance <= hard_distance)
    alpha = np.full(distance.shape, 255.0, dtype=np.float32)
    alpha[connected] = 0.0
    alpha_image = Image.fromarray(np.rint(alpha).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(radius=0.75)
    )
    alpha = np.asarray(alpha_image, dtype=np.float32)

    # Remove the warm studio color from antialiased edge pixels before compositing on
    # the apartment detail page. Fully transparent RGB values do not affect rendering.
    normalized_alpha = alpha[..., None] / 255.0
    corrected = rgb.copy()
    partial = (normalized_alpha[..., 0] > 0.04) & (normalized_alpha[..., 0] < 0.985)
    if np.any(partial):
        partial_alpha = normalized_alpha[partial]
        corrected[partial] = np.clip(
            (rgb[partial] - (1.0 - partial_alpha) * background_field[partial]) / partial_alpha,
            0.0,
            255.0,
        )

    rgba = np.dstack((np.rint(corrected).astype(np.uint8), np.rint(alpha).astype(np.uint8)))
    destination_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgba).save(
        destination_path,
        format="WEBP",
        quality=91,
        method=4,
        exact=True,
    )

    transparent = alpha <= 8
    opaque = alpha >= 247
    subject = alpha > 24
    height, width = alpha.shape
    if np.any(subject):
        ys, xs = np.nonzero(subject)
        bbox = f"{xs.min()},{ys.min()},{xs.max() + 1},{ys.max() + 1}"
    else:
        bbox = ""
    transparent_ratio = float(np.mean(transparent))
    partial_ratio = float(np.mean(~transparent & ~opaque))
    opaque_ratio = float(np.mean(opaque))
    status = "PASS"
    notes = []
    if not bbox:
        status = "REVIEW"
        notes.append("empty subject")
    if transparent_ratio < 0.05:
        status = "REVIEW"
        notes.append("too little transparent area")
    if transparent_ratio > 0.93:
        status = "REVIEW"
        notes.append("too much transparent area")
    corner_alpha = np.array(
        [alpha[0, 0], alpha[0, width - 1], alpha[height - 1, 0], alpha[height - 1, width - 1]]
    )
    if int(corner_alpha.max()) > 12:
        status = "REVIEW"
        notes.append("opaque corner")

    return ImageResult(
        complex_code=source_path.stem,
        input_path=str(source_path),
        output_path=str(destination_path),
        width=width,
        height=height,
        input_sha256=sha256(source_path),
        output_sha256=sha256(destination_path),
        background_rgb=",".join(str(int(round(value))) for value in background),
        soft_distance=round(soft_distance, 3),
        hard_distance=round(hard_distance, 3),
        transparent_ratio=round(transparent_ratio, 6),
        partial_ratio=round(partial_ratio, 6),
        opaque_ratio=round(opaque_ratio, 6),
        subject_bbox=bbox,
        status=status,
        note="; ".join(notes),
    )


def inspect_existing(source_path: Path, destination_path: Path) -> ImageResult:
    with Image.open(source_path) as opened:
        rgb = np.asarray(opened.convert("RGB"), dtype=np.float32)
    background, _, soft_distance, hard_distance = background_model(rgb)

    try:
        with Image.open(destination_path) as opened:
            rgba = np.asarray(opened.convert("RGBA"), dtype=np.uint8)
    except OSError:
        return remove_background(source_path, destination_path)
    if rgba.shape[:2] != rgb.shape[:2]:
        return remove_background(source_path, destination_path)
    alpha = rgba[..., 3].astype(np.float32)
    transparent = alpha <= 8
    opaque = alpha >= 247
    subject = alpha > 24
    height, width = alpha.shape
    if np.any(subject):
        ys, xs = np.nonzero(subject)
        bbox = f"{xs.min()},{ys.min()},{xs.max() + 1},{ys.max() + 1}"
    else:
        bbox = ""
    transparent_ratio = float(np.mean(transparent))
    partial_ratio = float(np.mean(~transparent & ~opaque))
    opaque_ratio = float(np.mean(opaque))
    status = "PASS"
    notes = []
    if not bbox:
        status = "REVIEW"
        notes.append("empty subject")
    if transparent_ratio < 0.05:
        status = "REVIEW"
        notes.append("too little transparent area")
    if transparent_ratio > 0.93:
        status = "REVIEW"
        notes.append("too much transparent area")
    corner_alpha = np.array(
        [alpha[0, 0], alpha[0, width - 1], alpha[height - 1, 0], alpha[height - 1, width - 1]]
    )
    if int(corner_alpha.max()) > 12:
        status = "REVIEW"
        notes.append("opaque corner")

    return ImageResult(
        complex_code=source_path.stem,
        input_path=str(source_path),
        output_path=str(destination_path),
        width=width,
        height=height,
        input_sha256=sha256(source_path),
        output_sha256=sha256(destination_path),
        background_rgb=",".join(str(int(round(value))) for value in background),
        soft_distance=round(soft_distance, 3),
        hard_distance=round(hard_distance, 3),
        transparent_ratio=round(transparent_ratio, 6),
        partial_ratio=round(partial_ratio, 6),
        opaque_ratio=round(opaque_ratio, 6),
        subject_bbox=bbox,
        status=status,
        note="; ".join(notes),
    )


def write_manifest(path: Path, results: list[ImageResult]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(asdict(results[0]).keys()))
        writer.writeheader()
        for result in results:
            writer.writerow(asdict(result))


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def write_migration(path: Path, results: list[ImageResult], version: str) -> None:
    values = ",\n".join(
        f"        ({sql_literal(result.complex_code)}, "
        f"{sql_literal(f'apartment-images/{version}/{result.complex_code}.webp')}, "
        f"{sql_literal(result.output_sha256)})"
        for result in results
    )
    sql = f"""-- BE-004: publish alpha-matted apartment illustrations with cache-busting object keys.
WITH image_source (complex_code, object_key, sha256) AS (
    VALUES
{values}
)
UPDATE apartment_image
SET object_key = image_source.object_key,
    sha256 = image_source.sha256
FROM image_source
JOIN apartment ON apartment.complex_code = image_source.complex_code
WHERE apartment_image.apartment_id = apartment.id;

DO $$
DECLARE
    migrated_count BIGINT;
BEGIN
    SELECT count(*) INTO migrated_count
    FROM apartment_image
    WHERE object_key LIKE 'apartment-images/{version}/%.webp';
    IF migrated_count <> {len(results)} THEN
        RAISE EXCEPTION 'Expected {len(results)} {version} apartment images, found %', migrated_count;
    END IF;
END
$$;
"""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(sql, encoding="utf-8", newline="\n")


def make_preview(path: Path, results: list[ImageResult]) -> None:
    selected_count = min(25, len(results))
    if selected_count == 1:
        selected = results
    else:
        selected = [
            results[round(index * (len(results) - 1) / (selected_count - 1))]
            for index in range(selected_count)
        ]
    cell_width, image_height, label_height = 256, 384, 24
    cell_height = image_height + label_height
    columns = 5
    rows = math.ceil(len(selected) / columns)
    canvas = Image.new("RGB", (columns * cell_width, rows * cell_height), PAGE_BACKGROUND)
    for index, result in enumerate(selected):
        with Image.open(result.output_path) as opened:
            asset = opened.convert("RGBA")
        asset.thumbnail((cell_width, image_height), Image.Resampling.LANCZOS)
        x = (index % columns) * cell_width + (cell_width - asset.width) // 2
        row_y = (index // columns) * cell_height
        y = row_y + label_height + (image_height - asset.height) // 2
        ImageDraw.Draw(canvas).text((x + 4, row_y + 4), result.complex_code, fill=(21, 35, 27))
        canvas.paste(asset, (x, y), asset)
    path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(path, format="PNG")


def main() -> None:
    args = parse_args()
    input_dir = args.input_dir.resolve()
    output_dir = args.output_dir.resolve()
    files = sorted(input_dir.glob("*.webp"))
    if args.only:
        requested = {item.removesuffix(".webp") for item in args.only}
        files = [path for path in files if path.stem in requested]
        missing = sorted(requested - {path.stem for path in files})
        if missing:
            raise RuntimeError(f"Missing input images: {', '.join(missing)}")
    if args.limit is not None:
        files = files[: args.limit]
    if not files:
        raise RuntimeError("No input WebP files found")

    results = []
    with ProcessPoolExecutor(max_workers=args.workers) as executor:
        futures = {}
        for source in files:
            destination = output_dir / source.name
            operation = inspect_existing if args.resume and destination.is_file() else remove_background
            futures[executor.submit(operation, source, destination)] = source
        for completed, future in enumerate(as_completed(futures), start=1):
            result = future.result()
            results.append(result)
            if completed == len(files) or completed % 100 == 0:
                print(f"processed={completed}/{len(files)}")
    results.sort(key=lambda item: item.complex_code)
    write_manifest(args.manifest_out.resolve(), results)
    if args.migration_out:
        write_migration(args.migration_out.resolve(), results, args.version)
    if args.preview_out:
        make_preview(args.preview_out.resolve(), results)
    if args.review_preview_out:
        review_results = [result for result in results if result.status == "REVIEW"]
        if review_results:
            make_preview(args.review_preview_out.resolve(), review_results)

    summary = {
        "processed": len(results),
        "passed": sum(result.status == "PASS" for result in results),
        "review": sum(result.status == "REVIEW" for result in results),
        "averageTransparentRatio": round(
            sum(result.transparent_ratio for result in results) / len(results), 6
        ),
        "inputBytes": sum(Path(result.input_path).stat().st_size for result in results),
        "outputBytes": sum(Path(result.output_path).stat().st_size for result in results),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
