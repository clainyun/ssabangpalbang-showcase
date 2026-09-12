import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

/**
 * 팔방이 추천 임장 경로 API (BE-020).
 *
 * 타입은 docs/API.md 예시가 아니라 백엔드 응답 DTO 실물을 기준으로 정의했습니다
 * (backend/.../fieldvisit/dto/response/FieldVisitRouteDetailResponse.java,
 * RouteWaypointResponse.java, FieldVisitRouteGenerateResponse.java).
 *
 * 체크리스트를 경유지 순서(sequence 1, 2, 3...)로 묶어 보여주는 데 씁니다. 경유지와
 * 체크리스트 항목의 연결은 서버가 이미 해 두므로(waypoints[].myItems) 프론트에서
 * 다시 조합할 필요가 없습니다.
 */

/** 경유지에 배정된 내 체크리스트 항목. checklistItemId 는 체크 저장 API와 같은 값입니다. */
export interface RouteChecklistItem {
  checklistItemId: number;
  category: string;
  title: string;
  subtitle: string;
  isCompleted: boolean;
  recordCount: number;
}

export interface RouteWaypoint {
  waypointId: number;
  /** 1부터 시작하는 방문 순서. (route_id, sequence) 가 유일하도록 서버가 보장합니다. */
  sequence: number;
  facilityType: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  kakaoPlaceId: string | null;
  distanceFromOriginM: number;
  distanceFromPrevM: number;
  walkMinutesFromPrev: number;
  stayMinutes: number;
  guide: string | null;
  myItems: RouteChecklistItem[];
  myItemCount: number;
  /** 다른 참여자에게만 배정된 항목 수. 목록은 내려오지 않습니다. */
  sharedItemCount: number;
}

export interface RouteProgress {
  completedWaypointCount: number;
  totalWaypointCount: number;
  completedItemCount: number;
  totalItemCount: number;
}

export interface FieldVisitRoute {
  sessionId: number;
  routeId: number;
  generatedAt: string;
  totalDistanceMeters: number;
  estimatedDurationMinutes: number;
  origin: { apartmentId: number; name: string; latitude: number; longitude: number };
  /**
   * 경유지를 순서대로 잇는 보행 경로(GeoJSON LineString, 좌표는 [경도, 위도]).
   * 카카오 보행 경로 응답을 서버가 그대로 저장한 값이라, 실패했으면 null 입니다.
   */
  geometry: GeoJSON.LineString | null;
  myProgress: RouteProgress;
  waypoints: RouteWaypoint[];
}

export interface FieldVisitRouteDetail {
  studyId: number;
  readOnly: boolean;
  /** 아직 생성 전이거나 세션이 시작되지 않았으면 null 입니다(오류가 아닙니다). */
  route: FieldVisitRoute | null;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class FieldVisitRouteApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'FieldVisitRouteApiError';
    this.code = code;
  }
}

async function request<T>(
  studyId: number,
  init: RequestInit,
  fallbackMessage: string,
): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/studies/${studyId}/field-visit/route`, {
      headers: { Accept: 'application/json' },
      ...init,
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitRouteApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitRouteApiError(body?.code ?? 'UNKNOWN', body?.message ?? fallbackMessage);
  }

  return body.data;
}

/**
 * GET — 활성 스터디 멤버면 조회할 수 있습니다. 경로가 없어도 오류가 아니라
 * route: null 인 정상 응답이 옵니다.
 */
export function getFieldVisitRoute(studyId: number): Promise<FieldVisitRouteDetail> {
  return request(studyId, { method: 'GET' }, '경로를 불러오지 못했습니다.');
}

/**
 * POST — 진행 중인 임장 참여자만 생성할 수 있습니다. 이미 있으면 200
 * ROUTE_ALREADY_EXISTS 로 기존 경로가 그대로 오므로 오류로 다루지 않습니다.
 *
 * 생성은 카카오 POI·보행 경로 같은 외부 서비스를 타므로 503 으로 실패할 수 있고,
 * 체크리스트가 없거나(409 ROUTE_CHECKLIST_REQUIRED) 경유지가 2곳 미만이면
 * (409 ROUTE_NOT_APPLICABLE) 만들지 못합니다. 호출부에서 메시지를 그대로 보여 줍니다.
 */
export function generateFieldVisitRoute(studyId: number): Promise<FieldVisitRoute> {
  return request(studyId, { method: 'POST' }, '경로를 만들지 못했습니다.');
}
