import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import * as ts from 'typescript';

const modelSource = await readFile(new URL('./checklistModel.ts', import.meta.url), 'utf8');
const { outputText: modelJavaScript } = ts.transpileModule(modelSource, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
});
const model = await import(
  `data:text/javascript;base64,${Buffer.from(modelJavaScript).toString('base64')}`
);
const {
  ChecklistRefreshContextError,
  checklistBodyFromGeneration,
  generateAndRefreshChecklist,
  resolveChecklistAfterGeneration,
} = model;

function generationResult(isFallback = false) {
  return {
    studyId: 7,
    sessionId: 100,
    checklistId: 55,
    isFallback,
    generatedAt: '2026-08-05T12:00:00+09:00',
    completedCount: 1,
    totalCount: 2,
    categories: [
      {
        category: '교통',
        itemCount: 2,
        items: [
          {
            checklistItemId: 501,
            title: '지하철역 접근성',
            subtitle: '도보 시간을 확인해 주세요.',
            displayOrder: 1,
            isCompleted: true,
            completedAt: '2026-08-05T12:01:00+09:00',
            recordCount: 2,
          },
          {
            checklistItemId: 502,
            title: '출퇴근 혼잡',
            subtitle: null,
            displayOrder: 2,
            isCompleted: false,
            completedAt: null,
            recordCount: 0,
          },
        ],
      },
    ],
  };
}

test('AI 생성 응답의 공개 필드를 조회 화면 모델에 보존한다', () => {
  const generated = generationResult();
  const body = checklistBodyFromGeneration(generated);

  assert.equal(body.isFallback, false);
  assert.deepEqual(body.categories[0], {
    category: '교통',
    completedCount: 1,
    totalCount: 2,
    items: generated.categories[0].items,
  });
  assert.equal(body.categories[0].items[1].subtitle, null);
  assert.equal(body.categories[0].items[0].recordCount, 2);
});

test('fallback 생성도 AI 생성과 동일한 화면 모델을 사용한다', () => {
  const aiBody = checklistBodyFromGeneration(generationResult(false));
  const fallbackBody = checklistBodyFromGeneration(generationResult(true));

  assert.equal(fallbackBody.isFallback, true);
  assert.deepEqual(fallbackBody.categories, aiBody.categories);
});

test('생성 직후 재조회한 공개 DTO를 최종 상태로 우선한다', () => {
  const generated = generationResult();
  const generatedBody = checklistBodyFromGeneration(generated);
  const refreshedBody = {
    ...generatedBody,
    completedCount: 2,
    categories: generatedBody.categories.map((category) => ({
      ...category,
      completedCount: 2,
    })),
  };
  const resolved = resolveChecklistAfterGeneration(generated, generatedBody, {
    studyId: 7,
    sessionId: 100,
    participantStatus: 'ENDED',
    readOnly: true,
    checklist: refreshedBody,
  });

  assert.equal(resolved.body, refreshedBody);
  assert.equal(resolved.readOnly, true);
});

test('재조회가 아직 null이면 생성 결과를 유지하고 최신 readOnly만 반영한다', () => {
  const generated = generationResult();
  const generatedBody = checklistBodyFromGeneration(generated);
  const resolved = resolveChecklistAfterGeneration(generated, generatedBody, {
    studyId: 7,
    sessionId: 100,
    participantStatus: 'ENDED',
    readOnly: true,
    checklist: null,
  });

  assert.equal(resolved.body, generatedBody);
  assert.equal(resolved.readOnly, true);
});

test('재조회 네트워크 실패는 생성 결과를 유지하고 편집을 잠근다', () => {
  const generated = generationResult(true);
  const generatedBody = checklistBodyFromGeneration(generated);
  const resolved = resolveChecklistAfterGeneration(generated, generatedBody, null);

  assert.equal(resolved.body, generatedBody);
  assert.equal(resolved.readOnly, true);
});

test('재조회가 다른 세션 또는 체크리스트를 반환하면 결합하지 않는다', () => {
  const generated = generationResult();
  const generatedBody = checklistBodyFromGeneration(generated);

  assert.throws(
    () =>
      resolveChecklistAfterGeneration(generated, generatedBody, {
        studyId: 7,
        sessionId: 101,
        participantStatus: 'IN_PROGRESS',
        readOnly: false,
        checklist: { ...generatedBody, checklistId: 99 },
      }),
    ChecklistRefreshContextError,
  );
});

test('생성 완료 알림 뒤 공개 조회를 실행하고 조회 DTO를 반환한다', async () => {
  const calls = [];
  const generated = generationResult();
  const generatedBody = checklistBodyFromGeneration(generated);
  const refreshedDetail = {
    studyId: 7,
    sessionId: 100,
    participantStatus: 'IN_PROGRESS',
    readOnly: false,
    checklist: generatedBody,
  };

  const resolved = await generateAndRefreshChecklist({
    generate: async () => {
      calls.push('generate');
      return generated;
    },
    onGenerated: () => calls.push('onGenerated'),
    refresh: async () => {
      calls.push('refresh');
      return refreshedDetail;
    },
    isNetworkError: () => false,
  });

  assert.deepEqual(calls, ['generate', 'onGenerated', 'refresh']);
  assert.equal(resolved.body, refreshedDetail.checklist);
});

test('공개 재조회 네트워크 오류만 생성 결과로 복구한다', async () => {
  const networkError = new Error('offline');
  const generated = generationResult(true);
  const resolved = await generateAndRefreshChecklist({
    generate: async () => generated,
    onGenerated: () => undefined,
    refresh: async () => {
      throw networkError;
    },
    isNetworkError: (error) => error === networkError,
  });

  assert.equal(resolved.body.checklistId, generated.checklistId);
  assert.equal(resolved.readOnly, true);

  await assert.rejects(
    generateAndRefreshChecklist({
      generate: async () => generated,
      onGenerated: () => undefined,
      refresh: async () => {
        throw new Error('server error');
      },
      isNetworkError: () => false,
    }),
    /server error/,
  );
});
