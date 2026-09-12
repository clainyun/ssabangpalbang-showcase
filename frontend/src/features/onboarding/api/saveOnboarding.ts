import {
  authenticatedFetch,
  rethrowSessionFailure,
} from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

export type Purpose = 'RESIDENCE' | 'INVESTMENT' | 'STUDY';
export type MaritalStatus = 'SINGLE' | 'MARRIED';
export type AgeGroup =
  | 'TEENS'
  | 'TWENTIES'
  | 'THIRTIES'
  | 'FORTIES'
  | 'FIFTIES'
  | 'SIXTIES_PLUS';
export type Priority =
  | 'TRANSPORT'
  | 'SAFETY'
  | 'EDUCATION'
  | 'COMMERCIAL'
  | 'WALKABILITY'
  | 'GREEN_SPACE'
  | 'PARKING'
  | 'NOISE';
export type CharacterId = 'PALBANG' | 'PALBANG_RABBIT' | 'PALBANG_DOG';

export interface OnboardingRequest {
  purpose: Purpose;
  maritalStatus: MaritalStatus;
  hasVehicle: boolean;
  hasChildren: boolean;
  priorities: Priority[];
  ageGroup: AgeGroup;
  ageGroupPublicAgreed: boolean;
  selectedCharacterId: CharacterId;
}

export interface OnboardingResponseData {
  memberId: number;
  purpose: Purpose;
  maritalStatus: MaritalStatus;
  hasVehicle: boolean;
  hasChildren: boolean;
  priorities: Priority[];
  ageGroup: AgeGroup;
  ageGroupPublicAgreed: boolean;
  selectedCharacterId: CharacterId;
  onboardingCompleted: boolean;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

interface FieldErrorData {
  field: string;
  reason: string;
}

interface AllowedValuesErrorData {
  allowedValues: string[];
}

export type OnboardingErrorField =
  | 'purpose'
  | 'maritalStatus'
  | 'hasVehicle'
  | 'hasChildren'
  | 'priorities'
  | 'ageGroup'
  | 'ageGroupPublicAgreed'
  | 'selectedCharacterId';

const ONBOARDING_ERROR_FIELDS: readonly OnboardingErrorField[] = [
  'purpose',
  'maritalStatus',
  'hasVehicle',
  'hasChildren',
  'priorities',
  'ageGroup',
  'ageGroupPublicAgreed',
  'selectedCharacterId',
];

/** field 가 있으면 그 값이 속한 단계로 돌아가서 에러를 표시하고,
 * 없으면 마지막 단계(캐릭터 선택)에 공통 에러로 표시합니다. */
export class OnboardingError extends Error {
  code: string;
  field?: OnboardingErrorField;
  allowedValues?: string[];

  constructor(
    code: string,
    message: string,
    field?: OnboardingErrorField,
    allowedValues?: string[],
  ) {
    super(message);
    this.code = code;
    this.field = field;
    this.allowedValues = allowedValues;
  }
}

function isFieldErrorData(data: unknown): data is FieldErrorData {
  return (
    typeof data === 'object' &&
    data !== null &&
    'field' in data &&
    'reason' in data &&
    typeof (data as FieldErrorData).field === 'string'
  );
}

function isAllowedValuesErrorData(data: unknown): data is AllowedValuesErrorData {
  return (
    typeof data === 'object' &&
    data !== null &&
    'allowedValues' in data &&
    Array.isArray((data as AllowedValuesErrorData).allowedValues)
  );
}

function toOnboardingErrorField(field: string): OnboardingErrorField | undefined {
  return ONBOARDING_ERROR_FIELDS.includes(field as OnboardingErrorField)
    ? (field as OnboardingErrorField)
    : undefined;
}

export async function saveOnboarding(
  request: OnboardingRequest,
): Promise<OnboardingResponseData> {
  // 컴포넌트가 아니라 일반 함수라 훅 대신 getState()로 현재 값을 읽습니다
  // (zustand는 이 방식을 공식 지원합니다).
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      '/api/v1/members/me/onboarding',
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    // fetch 자체가 실패한 경우(오프라인 등)만 여기로 옵니다.
    // 서버가 응답을 준 경우(4xx/5xx 포함)는 아래 !response.ok 분기에서 따로 처리합니다.
    throw new OnboardingError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    OnboardingResponseData | FieldErrorData | AllowedValuesErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING' && isFieldErrorData(body?.data)) {
      throw new OnboardingError(code, body.data.reason, toOnboardingErrorField(body.data.field));
    }
    if (code === 'MEMBER_CHARACTER_INVALID' && isAllowedValuesErrorData(body?.data)) {
      throw new OnboardingError(
        code,
        body?.message || '선택할 수 없는 캐릭터입니다.',
        'selectedCharacterId',
        body.data.allowedValues,
      );
    }
    if (code === 'MEMBER_AGE_GROUP_INVALID' && isAllowedValuesErrorData(body?.data)) {
      throw new OnboardingError(
        code,
        body?.message || '선택할 수 없는 연령대입니다.',
        'ageGroup',
        body.data.allowedValues,
      );
    }
    // UI에서 이미 중복 선택 자체를 막고 있어 정상 플로우에서는 발생하지 않는 방어 코드입니다.
    if (code === 'MEMBER_PRIORITY_DUPLICATED') {
      throw new OnboardingError(
        code,
        body?.message || '같은 우선순위를 중복해서 선택할 수 없습니다.',
        'priorities',
      );
    }
    if (code === 'AUTH_MEMBER_WITHDRAWN') {
      throw new OnboardingError(code, body?.message || '탈퇴 처리된 회원입니다.');
    }
    throw new OnboardingError(
      code,
      body?.message || '온보딩 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.',
    );
  }

  return body.data as OnboardingResponseData;
}
