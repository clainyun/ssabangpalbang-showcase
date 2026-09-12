import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { HomeApiError, type HomeSummary } from '@/features/home/types';

export interface HomeLocation {
  latitude: number;
  longitude: number;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

const LOCATION_ERROR_CODES = new Set([
  'HOME_LOCATION_INCOMPLETE',
  'HOME_LATITUDE_INVALID',
  'HOME_LONGITUDE_INVALID',
]);

async function requestHomeSummary(location: HomeLocation | null): Promise<HomeSummary> {
  const measureStartedAt = Date.now();
  let measurementOk = false;

  // 콜드 스타트 계측 로그. 개발 빌드에서만 남깁니다(운영 핫패스에서는 실행 자체를 안 함).
  if (__DEV__) {
    console.info('[ColdStartData] home:start', {
      epochMs: measureStartedAt,
      hasLocation: location !== null,
    });
  }

  try {
    const { accessToken, sessionVersion } = useAuthStore.getState();

    const query = new URLSearchParams();
    if (location !== null) {
      query.set('latitude', String(location.latitude));
      query.set('longitude', String(location.longitude));
    }
    const queryString = query.toString();

    let response: Response;
    try {
      response = await authenticatedFetch(
        `/api/v1/home${queryString ? `?${queryString}` : ''}`,
        { method: 'GET' },
        { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
      );
    } catch (error) {
      rethrowSessionFailure(error);
      throw new HomeApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
    }

    const body = (await response.json().catch(() => null)) as ApiEnvelope<HomeSummary> | null;

    if (
      !response.ok ||
      body === null ||
      !body.success ||
      body.data === null ||
      body.data === undefined
    ) {
      throw new HomeApiError(
        body?.code ?? 'UNKNOWN',
        body?.message ?? '홈 화면 정보를 불러오지 못했습니다.',
      );
    }

    measurementOk = true;
    return body.data;
  } finally {
    if (__DEV__) {
      const measureCompletedAt = Date.now();
      console.info('[ColdStartData] home:complete', {
        epochMs: measureCompletedAt,
        durationMs: measureCompletedAt - measureStartedAt,
        hasLocation: location !== null,
        ok: measurementOk,
      });
    }
  }
}

/**
 * 위치 파라미터가 있는데도 위경도 오류(400)가 나면, 정상 흐름에서는 발생하지 않아야 하는
 * 방어 경로로 보고 위치 없이 한 번 더 시도합니다 (docs/API.md HOME_LOCATION_* 참고).
 */
export async function getHomeSummary(location: HomeLocation | null): Promise<HomeSummary> {
  try {
    return await requestHomeSummary(location);
  } catch (error) {
    if (location !== null && error instanceof HomeApiError && LOCATION_ERROR_CODES.has(error.code)) {
      return requestHomeSummary(null);
    }
    throw error;
  }
}
