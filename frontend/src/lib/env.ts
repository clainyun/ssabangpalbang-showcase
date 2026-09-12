/**
 * EXPO_PUBLIC_ 접두어가 붙은 값은 빌드 시 앱 번들에 인라인됩니다.
 * 따라서 비밀값을 넣지 마십시오 (§5.3).
 */
function required(name: string, value: string | undefined): string {
  if (!value) {
    throw new Error(`환경변수 ${name} 가 없습니다. frontend/.env.local 을 확인하십시오.`);
  }
  return value;
}

export const env = {
  mapboxToken: required('EXPO_PUBLIC_MAPBOX_TOKEN', process.env.EXPO_PUBLIC_MAPBOX_TOKEN),
  apiBaseUrl: required('EXPO_PUBLIC_API_BASE_URL', process.env.EXPO_PUBLIC_API_BASE_URL),
  wsUrl: required('EXPO_PUBLIC_WS_URL', process.env.EXPO_PUBLIC_WS_URL),
  kakaoAppKey: process.env.EXPO_PUBLIC_KAKAO_APP_KEY ?? '',
  // 카카오 인가 코드 요청(kauth.kakao.com/oauth/authorize)의 client_id로 쓰는 REST API 키.
  // 네이티브 앱 키(kakaoAppKey)와는 다른 값입니다 — 백엔드 oauth.kakao.client-id와 동일한 키를 씁니다.
  kakaoRestApiKey: process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY ?? '',
  // 백엔드 oauth.naver.client-id와 동일한 키를 씁니다.
  naverClientId: process.env.EXPO_PUBLIC_NAVER_CLIENT_ID ?? '',
  // 시연 전용 가상 위치 기능. 운영 빌드에서는 반드시 false 또는 미설정으로 둡니다.
  demoModeEnabled: process.env.EXPO_PUBLIC_DEMO_MODE_ENABLED === 'true',
} as const;
