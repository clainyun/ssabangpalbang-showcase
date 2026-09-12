export const FLOATING_TAB_BAR_HEIGHT = 70;
export const FLOATING_TAB_BAR_MIN_BOTTOM_MARGIN = 18;
export const FLOATING_TAB_BAR_SAFE_AREA_GAP = 8;
export const FLOATING_TAB_BAR_OVERLAY_GAP = 8;

export function getFloatingTabBarBottomMargin(safeAreaBottom: number): number {
  return Math.max(
    FLOATING_TAB_BAR_MIN_BOTTOM_MARGIN,
    safeAreaBottom + FLOATING_TAB_BAR_SAFE_AREA_GAP,
  );
}

/** 탭 바 위에 배치되는 플로팅 요소가 확보해야 하는 최소 화면 하단 여백. */
export function getFloatingTabBarOverlayBottom(safeAreaBottom: number): number {
  return (
    getFloatingTabBarBottomMargin(safeAreaBottom) +
    FLOATING_TAB_BAR_HEIGHT +
    FLOATING_TAB_BAR_OVERLAY_GAP
  );
}
