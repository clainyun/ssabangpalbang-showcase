import { authenticatedFetch } from '@/lib/authenticatedFetch';

export type MediaFileUsage =
  | 'FIELD_PHOTO'
  | 'STT_AUDIO'
  | 'CHAT_IMAGE'
  | 'POST_ATTACHMENT';

export type MediaUploadStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'FAILED'
  | 'DELETED';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export interface PrepareMediaUploadInput {
  fileUsage: MediaFileUsage;
  originalName?: string;
  contentType: string;
  sizeBytes: number;
  studyId?: number;
}

export interface PreparedMediaUpload {
  fileId: number;
  fileUsage: MediaFileUsage;
  uploadUrl: string;
  method: 'PUT';
  requiredHeaders: Record<string, string>;
  uploadStatus: 'PENDING';
  uploadUrlExpiresAt: string;
  fileExpiresAt: string | null;
}

export interface CompletedMediaFile {
  fileId: number;
  fileUsage: MediaFileUsage;
  originalName: string | null;
  contentType: string;
  sizeBytes: number;
  uploadStatus: MediaUploadStatus;
  accessUrl: string | null;
  accessUrlExpiresAt: string | null;
  fileExpiresAt: string | null;
  createdAt: string;
}

export interface UploadMediaInput extends PrepareMediaUploadInput {
  localUri: string;
  uploadTimeoutMs?: number;
}

export class MediaUploadError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly fileId?: number,
  ) {
    super(message);
    this.name = 'MediaUploadError';
  }
}

export async function prepareMediaUpload(
  accessToken: string,
  input: PrepareMediaUploadInput,
  expectedSessionVersion?: number,
): Promise<PreparedMediaUpload> {
  const response = await authenticatedFetch(
    '/api/v1/media/presigned-urls',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
    {
      fallbackAccessToken: accessToken,
      expectedSessionVersion,
    },
  );
  return parseMediaResponse(
    response,
    '업로드 URL을 발급받지 못했습니다.',
  );
}

export async function putMediaFile(
  prepared: PreparedMediaUpload,
  localUri: string,
  contentType: string,
  timeoutMs = 240_000,
): Promise<void> {
  const localFile = await fetch(localUri);
  if (!localFile.ok) {
    throw new MediaUploadError(
      'MEDIA_LOCAL_FILE_READ_FAILED',
      '선택한 파일을 읽지 못했습니다.',
    );
  }
  const rawBlob = await localFile.blob();
  const headers = new Headers(prepared.requiredHeaders);
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', contentType);
  }
  // React Native의 fetch는 Blob 바디를 올릴 때 지정한 헤더가 아니라 Blob.type을
  // Content-Type으로 전송합니다. file:// blob은 type이 비어 있어 S3에
  // binary/octet-stream으로 저장되고, 게이트웨이 verify가 저장된 Content-Type과
  // 발급 시 선언한 값이 달라 MEDIA_CONTENT_TYPE_INVALID로 거부합니다.
  // presigned URL이 검증하는 Content-Type과 동일한 type의 Blob으로 감싸 방지합니다.
  const uploadContentType = headers.get('Content-Type') ?? contentType;
  const body =
    rawBlob.type === uploadContentType
      ? rawBlob
      : new Blob([rawBlob], { type: uploadContentType });

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(prepared.uploadUrl, {
      method: prepared.method,
      headers,
      body,
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new MediaUploadError(
        'MEDIA_PRESIGNED_PUT_FAILED',
        '파일 업로드에 실패했습니다.',
        prepared.fileId,
      );
    }
  } catch (error) {
    if (error instanceof MediaUploadError) {
      throw error;
    }
    throw new MediaUploadError(
      controller.signal.aborted
        ? 'MEDIA_PRESIGNED_PUT_TIMEOUT'
        : 'MEDIA_PRESIGNED_PUT_FAILED',
      controller.signal.aborted
        ? '파일 업로드 시간이 초과되었습니다.'
        : '파일 업로드에 실패했습니다.',
      prepared.fileId,
    );
  } finally {
    clearTimeout(timeout);
  }
}

export async function completeMediaUpload(
  accessToken: string,
  fileId: number,
  expectedSessionVersion?: number,
): Promise<CompletedMediaFile> {
  const response = await authenticatedFetch(
    `/api/v1/media/${fileId}/complete`,
    { method: 'POST' },
    {
      fallbackAccessToken: accessToken,
      expectedSessionVersion,
    },
  );
  return parseMediaResponse(
    response,
    '업로드 완료 처리에 실패했습니다.',
  );
}

export async function uploadMedia(
  accessToken: string,
  input: UploadMediaInput,
  expectedSessionVersion?: number,
): Promise<CompletedMediaFile> {
  const prepared = await prepareMediaUpload(
    accessToken,
    input,
    expectedSessionVersion,
  );
  await putMediaFile(
    prepared,
    input.localUri,
    input.contentType,
    input.uploadTimeoutMs,
  );
  try {
    return await completeMediaUpload(
      accessToken,
      prepared.fileId,
      expectedSessionVersion,
    );
  } catch (error) {
    if (error instanceof MediaUploadError) {
      throw new MediaUploadError(
        error.code,
        error.message,
        prepared.fileId,
      );
    }
    throw error;
  }
}

async function parseMediaResponse<T>(
  response: Response,
  fallbackMessage: string,
): Promise<T> {
  const body = (await response.json().catch(() => null)) as
    | ApiEnvelope<T>
    | null;
  if (
    !response.ok ||
    body === null ||
    !body.success ||
    body.data === null
  ) {
    throw new MediaUploadError(
      body?.code ?? 'COMMON_NETWORK_ERROR',
      body?.message || fallbackMessage,
    );
  }
  return body.data;
}
