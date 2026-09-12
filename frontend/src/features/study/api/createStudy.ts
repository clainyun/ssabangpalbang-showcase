import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import {
  type ApiEnvelope,
  type CreateStudyInput,
  StudyApiError,
  type StudyCreateResult,
} from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const CREATE_ERROR_MESSAGE = '잠시 후 다시 시도해주세요.';

export async function createStudy(
  input: CreateStudyInput,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyCreateResult> {
  let response: Response;

  try {
    response = await authenticatedFetch(
      '/api/v1/studies',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(input),
      },
      {
        fallbackAccessToken: accessToken,
        expectedSessionVersion: sessionVersion,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<StudyCreateResult> | null;
  const responseMessage = body?.message?.trim();

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(
      body?.code ?? 'COMMON_NETWORK_ERROR',
      responseMessage || CREATE_ERROR_MESSAGE,
    );
  }

  return body.data;
}
