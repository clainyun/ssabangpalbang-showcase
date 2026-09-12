import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import * as ts from 'typescript';

const source = await readFile(new URL('./savePendingTextDrafts.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
});
const { savePendingTextDrafts } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('종료 전 비어 있지 않은 초안을 trim해 모두 저장한다', async () => {
  const created = [];
  const saved = [];
  const refreshed = [];
  let requestSequence = 0;

  await savePendingTextDrafts({
    recordDrafts: { 11: '  역이 가까워요  ', 12: '   ', 13: '주차 진입로가 좁아요' },
    createClientRequestId: () => `request-${++requestSequence}`,
    createTextRecord: async (checklistItemId, text, clientRequestId) => {
      created.push({ checklistItemId, text, clientRequestId });
    },
    onDraftSaved: (checklistItemId, originalDraft) => {
      saved.push({ checklistItemId, originalDraft });
    },
    refreshRecordCount: async (checklistItemId) => {
      refreshed.push(checklistItemId);
    },
  });

  assert.deepEqual(created, [
    { checklistItemId: 11, text: '역이 가까워요', clientRequestId: 'request-1' },
    { checklistItemId: 13, text: '주차 진입로가 좁아요', clientRequestId: 'request-2' },
  ]);
  assert.deepEqual(saved, [
    { checklistItemId: 11, originalDraft: '  역이 가까워요  ' },
    { checklistItemId: 13, originalDraft: '주차 진입로가 좁아요' },
  ]);
  assert.deepEqual(refreshed, [11, 13]);
});

test('초안 하나라도 저장 실패하면 종료 호출자가 중단할 수 있도록 실패를 반환한다', async () => {
  const saveFailure = new Error('save failed');
  const saved = [];

  await assert.rejects(
    savePendingTextDrafts({
      recordDrafts: { 11: '성공 메모', 12: '실패 메모' },
      createClientRequestId: () => 'request-id',
      createTextRecord: async (checklistItemId) => {
        if (checklistItemId === 12) throw saveFailure;
      },
      onDraftSaved: (checklistItemId) => saved.push(checklistItemId),
      refreshRecordCount: async () => undefined,
    }),
    (error) => error === saveFailure,
  );
  assert.deepEqual(saved, [11]);
});

test('저장 후 배지 갱신만 실패하면 저장 성공을 유지한다', async () => {
  const saved = [];

  await savePendingTextDrafts({
    recordDrafts: { 11: '저장할 메모' },
    createClientRequestId: () => 'request-id',
    createTextRecord: async () => undefined,
    onDraftSaved: (checklistItemId) => saved.push(checklistItemId),
    refreshRecordCount: async () => {
      throw new Error('refresh failed');
    },
  });

  assert.deepEqual(saved, [11]);
});
