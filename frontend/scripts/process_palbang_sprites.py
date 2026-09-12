"""팔방이 신규 3D 캐릭터(palbang_plain/palbang_dog/palbang_rabbit) 스프라이트 전처리.

기존 process_dog_sprites.py / process_rabbit_sprites.py와 같은 규격(CELL=256,
TARGET_H=208, BOTTOM_PAD=16, 발 기준선·가로 중심 정렬)을 따르되, 배경 제거는
검정 임계값 flood-fill 대신 rembg(u2net, 세일리언시 기반 세그멘테이션)를 씁니다.

왜 rembg인가: 기존 스크립트들의 remove_black_bg는 "테두리에 닿은 어두운 픽셀"만
지우는 방식이라, 가방 같은 진한 색 소품이 배경과 밝기가 비슷하면 통째로 잘리거나
(process_rabbit_sprites.py의 DARK_THRESHOLD 60→25 튜닝 이력, SMALL_COMPONENT
관련 주석 참고) 반대로 프린지가 남는 문제가 반복됐습니다. rembg는 색상 임계값이
아니라 학습된 전경/배경 분리라 이런 소품을 몸통과 한 덩어리로 인식해 더 안정적으로
잘라냅니다.

원본(palbang_*/*.png)은 보존하고, palbang_*/processed/ 에 결과를 남깁니다.
"""
from __future__ import annotations

import argparse
import json
import os

import numpy as np
import scipy.ndimage as ndi
from PIL import Image
from rembg import new_session, remove

CHARACTERS_DIR = r"C:\Users\SSAFY\Desktop\S15P11A701\frontend\assets\images\characters"

CELL = 256          # 정사각 셀 (spriteConfig.ts SPRITE_FRAME_SIZE)
TARGET_H = 208       # 캐릭터 목표 높이(px)
BOTTOM_PAD = 16      # 발 아래 여백
BASELINE_Y = CELL - BOTTOM_PAD

# rembg 컷아웃 후에도 남는 잔점(경계 노이즈) 제거 기준. 가방·팻말류 소품은
# 수백~수천 px 라 이 값보다 훨씬 크므로 안전하게 살아남습니다.
SMALL_COMPONENT_PIXEL_THRESHOLD = 60

WALK_DIRECTIONS = ("n", "ne", "e", "se", "s", "sw", "w", "nw")
IDLE_VARIANTS = (1, 2, 3)

# (원본 폴더, processed 안 파일명 접두사) — 접두사는 기존 dog/rabbit_2 관례를 그대로 따릅니다.
CHARACTERS = (
    ("palbang_plain", "plain"),
    ("palbang_dog", "dog"),
    ("palbang_rabbit", "rabbit"),
)


def remove_small_components(rgba: np.ndarray) -> np.ndarray:
    """rembg 컷아웃 후 남는 작은 잔점만 제거한다(몸통과 떨어진 소품은 크기로 보존)."""
    a = rgba.copy()
    mask = a[:, :, 3] > 20
    if not mask.any():
        return a
    lbl, n = ndi.label(mask)
    if n <= 1:
        return a
    sizes = ndi.sum(mask, lbl, range(1, n + 1))
    keep_labels = [i + 1 for i, s in enumerate(sizes) if s >= SMALL_COMPONENT_PIXEL_THRESHOLD]
    keep = np.isin(lbl, keep_labels)
    a[:, :, 3] = np.where(keep, a[:, :, 3], 0)
    return a


def col_segments(mask: np.ndarray, minw: int = 10) -> list[tuple[int, int]]:
    cols = mask.any(axis=0)
    segs, st = [], None
    for i, v in enumerate(cols):
        if v and st is None:
            st = i
        elif not v and st is not None:
            if i - st >= minw:
                segs.append((st, i - 1))
            st = None
    if st is not None and len(cols) - st >= minw:
        segs.append((st, len(cols) - 1))
    return segs


def tight_bbox(frame: Image.Image) -> tuple[int, int, int, int] | None:
    a = np.array(frame)
    m = a[:, :, 3] > 20
    if not m.any():
        return None
    ys, xs = np.where(m)
    return (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)


RAW_BG_LUMA_THRESHOLD_MIN = 24
RAW_BG_LUMA_MARGIN = 25


def estimate_background_threshold(arr: np.ndarray, corner_size: int = 10) -> int:
    """네 귀퉁이 픽셀로 이 이미지의 배경 밝기를 추정해 전경 판정 임계값을 잡는다.

    원본마다 배경이 순수 검정에 가까운 것도 있고 짙은 회색인 것도 있어서(예: dog
    대각선 재촬영본은 코너 밝기가 최대 69), 고정 임계값 하나로는 못 커버한다.
    """
    h, w, _ = arr.shape
    cs = min(corner_size, h // 4, w // 4)
    corners = np.concatenate(
        [
            arr[:cs, :cs].reshape(-1, 3),
            arr[:cs, -cs:].reshape(-1, 3),
            arr[-cs:, :cs].reshape(-1, 3),
            arr[-cs:, -cs:].reshape(-1, 3),
        ]
    )
    return max(RAW_BG_LUMA_THRESHOLD_MIN, int(corners.max()) + RAW_BG_LUMA_MARGIN)


WHITE_MARGIN_ROW_MIN_THRESHOLD = 200


def crop_to_dark_band(raw: Image.Image) -> Image.Image:
    """일부 원본은 위아래에 순백색 여백을 두고 가운데만 검정 배경 띠로 되어
    있습니다(예: dog 대각선 재촬영본). 이 여백은 밝은 픽셀이라 배경 밝기 임계값
    기준 전경 마스크에 걸려 프레임 간 간격이 좌우로 뻥 뚫린 것처럼 보이고, 6프레임이
    하나로 뭉쳐버립니다. 실제 배경(검정)이 있는 세로 범위만 남기고 잘라냅니다.
    이미 전체가 어두운 배경이면 사실상 원본 그대로 반환됩니다.
    """
    arr = np.array(raw)
    row_min = arr.min(axis=(1, 2))
    dark_rows = np.where(row_min < WHITE_MARGIN_ROW_MIN_THRESHOLD)[0]
    if len(dark_rows) == 0:
        return raw
    return raw.crop((0, int(dark_rows.min()), raw.width, int(dark_rows.max()) + 1))


def extract_frames(raw_path: str, session) -> list[Image.Image]:
    """원본(검정 배경) 위에서 먼저 프레임 6장을 안전하게 나눈 뒤, 낱장 각각에
    rembg를 돌립니다.

    한 장짜리 6인 시트를 통째로 rembg에 넣으면 모델이 인접 캐릭터 사이 간격을
    메우거나(프레임 수가 줄어듦) 팔다리·소품을 별도 성분으로 잘라내(프레임 수가
    늘어남) 프레임 경계가 어긋납니다. 원본의 단순 밝기 기준 분할은 이미 6장으로
    정확히 갈라지는 것을 확인했으므로, 분할은 원본에서 하고 rembg는 낱장 컷아웃
    에만 씁니다.
    """
    raw = Image.open(raw_path).convert("RGB")
    raw = crop_to_dark_band(raw)
    raw_arr = np.array(raw)
    threshold = estimate_background_threshold(raw_arr)
    raw_mask = raw_arr.max(axis=2) > threshold
    segs = col_segments(raw_mask)

    frames = []
    for x0, x1 in segs:
        crop = raw.crop((x0, 0, x1 + 1, raw.height))
        cut = remove(crop, session=session)
        a = remove_small_components(np.array(cut))
        frames.append(Image.fromarray(a, "RGBA"))
    return frames


def build_strip(frames: list[Image.Image]) -> Image.Image:
    bboxes = [tight_bbox(f) for f in frames]
    max_h = max(b[3] - b[1] for b in bboxes)
    scale = TARGET_H / max_h

    strip = Image.new("RGBA", (CELL * len(frames), CELL), (0, 0, 0, 0))
    for i, (f, b) in enumerate(zip(frames, bboxes)):
        crop = f.crop(b)
        nw = max(1, round(crop.width * scale))
        nh = max(1, round(crop.height * scale))
        crop = crop.resize((nw, nh), Image.LANCZOS)
        px = i * CELL + (CELL - nw) // 2
        py = BASELINE_Y - nh
        strip.alpha_composite(crop, (px, py))
    return strip, scale, int(max_h)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--folders",
        nargs="+",
        choices=[folder for folder, _ in CHARACTERS],
        help="지정한 캐릭터 폴더만 재처리(생략 시 전체)",
    )
    parser.add_argument(
        "--directions",
        nargs="+",
        choices=WALK_DIRECTIONS,
        help="지정한 걷기 방향만 재처리(생략 시 전체, idle은 항상 전체 처리)",
    )
    args = parser.parse_args()
    characters = (
        CHARACTERS
        if args.folders is None
        else tuple(job for job in CHARACTERS if job[0] in args.folders)
    )
    directions = WALK_DIRECTIONS if args.directions is None else tuple(args.directions)
    partial_run = args.directions is not None

    session = new_session("u2net")

    for folder, prefix in characters:
        src_dir = os.path.join(CHARACTERS_DIR, folder)
        out_dir = os.path.join(src_dir, "processed")
        os.makedirs(out_dir, exist_ok=True)
        meta_path = os.path.join(out_dir, "_meta.json")
        meta: dict[str, dict] = {}
        if partial_run and os.path.exists(meta_path):
            with open(meta_path, encoding="utf-8") as f:
                meta = json.load(f)

        jobs: list[tuple[str, str]] = [
            (f"{folder}_{d}.png", f"{prefix}_walk_{d}") for d in directions
        ]
        if not partial_run:
            jobs += [
                (f"{folder}_idle{v}.png", f"{prefix}_idle_s_{v}") for v in IDLE_VARIANTS
            ]

        for fname, out_name in jobs:
            raw_path = os.path.join(src_dir, fname)
            frames = extract_frames(raw_path, session)
            if len(frames) != 6:
                raise ValueError(
                    f"{fname}: 원본 분할 프레임 수={len(frames)}, 6이어야 함 (raw 밝기 임계값 재확인 필요)"
                )
            strip, scale, max_h = build_strip(frames)

            out_path = os.path.join(out_dir, out_name + "_processed.png")
            strip.save(out_path)
            meta[out_name] = {
                "frames": len(frames),
                "scale": round(scale, 3),
                "src_max_h": max_h,
                "size": list(strip.size),
            }
            print(f"[{folder}] {out_name:20} frames={len(frames)} scale={scale:.3f} -> {strip.size}")

        with open(os.path.join(out_dir, "_meta.json"), "w", encoding="utf-8") as f:
            json.dump(meta, f, indent=2, ensure_ascii=False)

    print("\n완료. palbang_*/processed/ 에 저장됨.")


if __name__ == "__main__":
    main()
