import type { ApiEnvelope } from './types';
import { CommunityApiError } from './types';

export async function parseCommunityResponse<T>(
  response: Response,
  fallbackMessage: string,
): Promise<T> {
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (
    !response.ok ||
    body === null ||
    !body.success ||
    body.data === null ||
    body.data === undefined
  ) {
    throw new CommunityApiError(
      body?.code ?? 'COMMON_NETWORK_ERROR',
      body?.message || fallbackMessage,
    );
  }

  return body.data;
}
