export type CalendarRailItemLayout = {
  x: number;
  width: number;
};

export function getCenteredCalendarRailOffset(
  item: CalendarRailItemLayout,
  viewportWidth: number,
  contentWidth: number,
): number {
  if (viewportWidth <= 0 || contentWidth <= 0 || item.width <= 0) return 0;

  const desiredOffset = item.x + item.width / 2 - viewportWidth / 2;
  const maximumOffset = Math.max(0, contentWidth - viewportWidth);

  return Math.min(Math.max(0, desiredOffset), maximumOffset);
}
