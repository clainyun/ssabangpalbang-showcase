export type ScheduleTimePickerField = 'start' | 'end';
export type ScheduleTimePickerState = { field: ScheduleTimePickerField; base: Date };

export function createScheduleTimePickerState(
  field: ScheduleTimePickerField,
  startAt: Date | null,
  endAt: Date | null,
): ScheduleTimePickerState | null {
  if (startAt === null) return null;
  const base =
    field === 'start'
      ? new Date(startAt)
      : new Date(endAt?.getTime() ?? startAt.getTime() + 60 * 60 * 1000);
  return { field, base };
}

export function resolveScheduleTimePickerSelection(
  picker: ScheduleTimePickerState | null,
  eventType: string,
  selected?: Date,
): { field: ScheduleTimePickerField; value: Date } | null {
  if (eventType !== 'set' || selected === undefined || picker === null) return null;

  const value = new Date(picker.base);
  value.setHours(selected.getHours(), selected.getMinutes(), 0, 0);
  return { field: picker.field, value };
}
