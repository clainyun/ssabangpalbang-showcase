import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

/**
 * 임장 체크리스트 항목별 현장 기록(사진·텍스트) API (FE-015).
 *
 * 음성 메모는 별도 비동기 STT 파이프라인이 완료한 뒤 같은 기록 목록에 합류합니다.
 */

export type FieldRecordSourceType = 'TEXT' | 'PHOTO' | 'STT';

export interface FieldRecordPhotoInfo {
  fileId: number;
  originalName: string | null;
  contentType: string;
  fileUrl: string;
  available: boolean;
  expiresAt: string;
}

export interface FieldRecordAuthor {
  memberId: number;
  nickname: string;
  selectedCharacterId: string;
}

export interface FieldRecord {
  sourceId: number;
  checklistItemId: number;
  sourceType: FieldRecordSourceType;
  textContent: string | null;
  photo: FieldRecordPhotoInfo | null;
  sttStatus: string | null;
  author: FieldRecordAuthor;
  isMine: boolean;
  canEdit: boolean;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FieldRecordListResult {
  studyId: number;
  sessionId: number;
  readOnly: boolean;
  content: FieldRecord[];
  nextCursor: string | null;
  hasNext: boolean;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class FieldRecordApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'FieldRecordApiError';
    this.code = code;
  }
}

async function request<T>(path: string, init: RequestInit, fallbackMessage: string): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(path, {
      headers: { Accept: 'application/json', ...(init.headers ?? {}) },
      ...init,
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldRecordApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldRecordApiError(body?.code ?? 'UNKNOWN', body?.message ?? fallbackMessage);
  }

  return body.data;
}

export function listFieldRecords(
  studyId: number,
  checklistItemId: number,
  cursor?: string,
  signal?: AbortSignal,
): Promise<FieldRecordListResult> {
  const query = new URLSearchParams({
    checklistItemId: String(checklistItemId),
    mineOnly: 'true',
    size: '50',
  });
  if (cursor !== undefined) query.set('cursor', cursor);

  return request<FieldRecordListResult>(
    `/api/v1/studies/${studyId}/field-visit/records?${query.toString()}`,
    { method: 'GET', signal },
    '저장된 메모를 불러오지 못했습니다.',
  );
}

/** 종료 원문 화면에서도 50건 제한에 잘리지 않도록 서버 커서를 끝까지 따라갑니다. */
export async function listAllFieldRecords(
  studyId: number,
  checklistItemId: number,
  signal?: AbortSignal,
): Promise<FieldRecordListResult> {
  const content: FieldRecord[] = [];
  const visitedCursors = new Set<string>();
  let cursor: string | undefined;
  let lastPage: FieldRecordListResult | null = null;

  do {
    const page = await listFieldRecords(studyId, checklistItemId, cursor, signal);
    content.push(...page.content);
    lastPage = page;

    if (!page.hasNext) break;
    const nextCursor = page.nextCursor?.trim();
    if (!nextCursor) {
      throw new FieldRecordApiError(
        'INVALID_NEXT_CURSOR',
        '저장된 메모의 다음 페이지를 불러오지 못했습니다.',
      );
    }
    if (visitedCursors.has(nextCursor)) {
      throw new FieldRecordApiError(
        'INVALID_NEXT_CURSOR',
        '저장된 메모의 다음 페이지를 불러오지 못했습니다.',
      );
    }
    visitedCursors.add(nextCursor);
    cursor = nextCursor;
  } while (true);

  if (lastPage === null) {
    throw new FieldRecordApiError('EMPTY_RESPONSE', '저장된 메모를 불러오지 못했습니다.');
  }
  return { ...lastPage, content, nextCursor: null, hasNext: false };
}

export function createTextRecord(
  studyId: number,
  checklistItemId: number,
  textContent: string,
  clientRequestId: string,
): Promise<FieldRecord> {
  return request<{ record: FieldRecord }>(
    `/api/v1/studies/${studyId}/field-visit/records`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        checklistItemId,
        sourceType: 'TEXT',
        textContent,
        clientRequestId,
      }),
    },
    '메모를 저장하지 못했습니다.',
  ).then((result) => result.record);
}

export function createPhotoRecord(
  studyId: number,
  checklistItemId: number,
  photoFileId: number,
  clientRequestId: string,
): Promise<FieldRecord> {
  return request<{ record: FieldRecord }>(
    `/api/v1/studies/${studyId}/field-visit/records`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        checklistItemId,
        sourceType: 'PHOTO',
        photoFileId,
        clientRequestId,
      }),
    },
    '사진을 저장하지 못했습니다.',
  ).then((result) => result.record);
}

export function deleteFieldRecord(studyId: number, recordId: number): Promise<void> {
  return request<unknown>(
    `/api/v1/studies/${studyId}/field-visit/records/${recordId}`,
    { method: 'DELETE' },
    '메모를 삭제하지 못했습니다.',
  ).then(() => undefined);
}
