interface SavePendingTextDraftsOptions {
  recordDrafts: Record<number, string>;
  createClientRequestId: () => string;
  createTextRecord: (
    checklistItemId: number,
    text: string,
    clientRequestId: string,
  ) => Promise<unknown>;
  onDraftSaved: (checklistItemId: number, originalDraft: string) => void;
  refreshRecordCount: (checklistItemId: number) => Promise<void>;
}

/** 임장 종료 전에 아직 저장하지 않은 텍스트 초안을 모두 영속화합니다. */
export async function savePendingTextDrafts({
  recordDrafts,
  createClientRequestId,
  createTextRecord,
  onDraftSaved,
  refreshRecordCount,
}: SavePendingTextDraftsOptions): Promise<void> {
  const drafts = Object.entries(recordDrafts)
    .map(([checklistItemId, draft]) => ({
      checklistItemId: Number(checklistItemId),
      original: draft,
      text: draft.trim(),
    }))
    .filter((draft) => draft.text.length > 0);

  const results = await Promise.allSettled(
    drafts.map(async (draft) => {
      await createTextRecord(draft.checklistItemId, draft.text, createClientRequestId());
      onDraftSaved(draft.checklistItemId, draft.original);
      // 기록 자체는 이미 저장됐으므로 배지 갱신 실패 때문에 종료를 막거나 같은 메모를
      // 다시 저장하게 만들지 않습니다. 다음 목록 조회에서 서버 count로 복구됩니다.
      await refreshRecordCount(draft.checklistItemId).catch(() => undefined);
    }),
  );
  const firstFailure = results.find(
    (result): result is PromiseRejectedResult => result.status === 'rejected',
  );
  if (firstFailure) throw firstFailure.reason;
}
