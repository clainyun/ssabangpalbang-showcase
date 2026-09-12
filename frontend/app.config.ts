import type { ConfigContext, ExpoConfig } from 'expo/config';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * 네이티브 설정의 단일 지점입니다 (§4.5, §8.2).
 * android/ 는 커밋하지 않으므로(§8.3) 여기에 없는 설정은 prebuild 시 사라집니다.
 *
 * 주의: tsconfig 가 moduleResolution "bundler" + react-native 조건이라
 * 릴리스 빌드에서는 Firebase 설정 파일의 존재와 Android 패키지 일치 여부도
 * 빌드 설정을 평가하는 Node.js 단계에서 확인합니다.
 */

const BUILD_PROFILE = (
  process.env.EAS_BUILD_PROFILE?.trim() ||
  process.env.APP_ENV?.trim() ||
  'development'
).toLowerCase();
const IS_RELEASE_BUILD = BUILD_PROFILE === 'preview' || BUILD_PROFILE === 'production';
const PLACEHOLDER_PATTERN = /(change_?me|replace_?with|placeholder)/i;
const ANDROID_PACKAGE = 'com.ssafy.ssabangpalbang';
const REQUIRED_RELEASE_PUBLIC_VALUES = [
  'EXPO_PUBLIC_MAPBOX_TOKEN',
  'EXPO_PUBLIC_KAKAO_APP_KEY',
  'EXPO_PUBLIC_KAKAO_REST_API_KEY',
  'EXPO_PUBLIC_NAVER_CLIENT_ID',
] as const;

function requireReleaseValue(name: string): string {
  // Centralized release validation intentionally reads a caller-selected key.
  // eslint-disable-next-line expo/no-dynamic-env-var
  const rawValue = process.env[name];
  const value = rawValue?.trim();

  if (!value) {
    throw new Error(`[${BUILD_PROFILE}] ${name} must be set before a release build.`);
  }

  if (rawValue !== value) {
    throw new Error(`[${BUILD_PROFILE}] ${name} must not contain surrounding whitespace.`);
  }

  if (PLACEHOLDER_PATTERN.test(value)) {
    throw new Error(`[${BUILD_PROFILE}] ${name} contains a placeholder value.`);
  }

  return value;
}

function requireReleaseUrl(name: string, protocol: 'https:' | 'wss:'): string {
  const value = requireReleaseValue(name);
  let parsed: URL;

  try {
    parsed = new URL(value);
  } catch {
    throw new Error(`[${BUILD_PROFILE}] ${name} must be an absolute URL.`);
  }

  if (parsed.protocol !== protocol) {
    throw new Error(`[${BUILD_PROFILE}] ${name} must use ${protocol.slice(0, -1)}.`);
  }

  if (parsed.username || parsed.password) {
    throw new Error(`[${BUILD_PROFILE}] ${name} must not contain credentials.`);
  }

  return value;
}

function validateGoogleServicesJson(): string | undefined {
  const rawPath = process.env.GOOGLE_SERVICES_JSON;

  // EAS secret file variables are unavailable while the local EAS CLI resolves
  // dynamic config. The build runner injects the real file path before prebuild.
  if (!rawPath) {
    if (process.env.EAS_BUILD === 'true') {
      throw new Error(`[${BUILD_PROFILE}] GOOGLE_SERVICES_JSON must be set on EAS Build.`);
    }
    return undefined;
  }

  const googleServicesJson = rawPath.trim();
  if (rawPath !== googleServicesJson) {
    throw new Error(
      `[${BUILD_PROFILE}] GOOGLE_SERVICES_JSON must not contain surrounding whitespace.`,
    );
  }

  if (googleServicesJson.startsWith('{')) {
    throw new Error(
      `[${BUILD_PROFILE}] GOOGLE_SERVICES_JSON must be a file path, not JSON content.`,
    );
  }

  const googleServicesPath = resolve(googleServicesJson);
  if (!existsSync(googleServicesPath)) {
    if (process.env.EAS_BUILD === 'true') {
      throw new Error(`[${BUILD_PROFILE}] GOOGLE_SERVICES_JSON file does not exist.`);
    }
    return googleServicesJson;
  }

  let googleServices: {
    client?: {
      client_info?: { android_client_info?: { package_name?: string } };
    }[];
  };
  try {
    googleServices = JSON.parse(readFileSync(googleServicesPath, 'utf8')) as typeof googleServices;
  } catch {
    throw new Error(`[${BUILD_PROFILE}] GOOGLE_SERVICES_JSON must contain valid JSON.`);
  }

  const matchesAndroidPackage = googleServices.client?.some(
    (client) => client.client_info?.android_client_info?.package_name === ANDROID_PACKAGE,
  );
  if (!matchesAndroidPackage) {
    throw new Error(
      `[${BUILD_PROFILE}] GOOGLE_SERVICES_JSON must contain an Android client for ${ANDROID_PACKAGE}.`,
    );
  }

  return googleServicesJson;
}

function validateReleaseEnvironment(): string | undefined {
  if (!IS_RELEASE_BUILD) {
    return process.env.GOOGLE_SERVICES_JSON?.trim() || undefined;
  }

  requireReleaseUrl('EXPO_PUBLIC_API_BASE_URL', 'https:');
  requireReleaseUrl('EXPO_PUBLIC_WS_URL', 'wss:');
  REQUIRED_RELEASE_PUBLIC_VALUES.forEach(requireReleaseValue);

  const mapboxToken = requireReleaseValue('EXPO_PUBLIC_MAPBOX_TOKEN');
  if (!/^pk\.[A-Za-z0-9._-]{20,}$/.test(mapboxToken)) {
    throw new Error(
      `[${BUILD_PROFILE}] EXPO_PUBLIC_MAPBOX_TOKEN must be a valid restricted public token.`,
    );
  }

  const demoMode = requireReleaseValue('EXPO_PUBLIC_DEMO_MODE_ENABLED').toLowerCase();
  if (demoMode !== 'false') {
    throw new Error(
      `[${BUILD_PROFILE}] EXPO_PUBLIC_DEMO_MODE_ENABLED must be exactly false for a release build.`,
    );
  }

  return validateGoogleServicesJson();
}

// google-services.json 은 credential 취급하며 Git에 커밋하지 않습니다.
// release 빌드 러너에서는 경로가 필수이고, EAS secret file 주입 직후 내용을 확인합니다.
const GOOGLE_SERVICES_JSON = validateReleaseEnvironment();

const KAKAO_APP_KEY = process.env.EXPO_PUBLIC_KAKAO_APP_KEY ?? '';
const EAS_PROJECT_ID = 'e93e1e1c-53c8-4005-a7f3-a881fd1efad5';
const ANDROID_NOTIFICATION_CHANNEL_ID = 'ssabangpalbang-alerts';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: '싸방팔방',
  slug: 'ssabangpalbang',
  scheme: 'ssabangpalbang', // 딥링크 (§6.1, §6.5)
  version: '1.0.1',
  orientation: 'portrait',
  icon: './assets/branding/icon.png',
  userInterfaceStyle: 'light',
  // newArchEnabled 는 SDK 57 의 ExpoConfig 타입에 더 이상 없습니다.
  // RN 0.82 부터 New Architecture 비활성화 옵션이 제거되어 항상 켜져 있습니다 (§8.1).

  android: {
    package: ANDROID_PACKAGE,
    versionCode: 1,
    intentFilters: [
      {
        action: 'VIEW',
        autoVerify: true,
        category: ['BROWSABLE', 'DEFAULT'],
        data: {
          scheme: 'https',
          host: 'portfolio.example.com',
          pathPrefix: '/open/report/',
        },
      },
    ],
    // edgeToEdgeEnabled 는 SDK 57 에서 제거되었습니다 — Android 16 이 강제하므로
    // 옵션 자체가 없어졌습니다. 지정하면 경고가 납니다. RN 0.86 을 고른 이유입니다 (§4.2).
    ...(GOOGLE_SERVICES_JSON ? { googleServicesFile: GOOGLE_SERVICES_JSON } : {}),
    permissions: [
      'android.permission.INTERNET',
      'android.permission.ACCESS_FINE_LOCATION', // §6.4 — 포그라운드만
      'android.permission.ACCESS_COARSE_LOCATION',
      'android.permission.POST_NOTIFICATIONS', // §6.5 — Android 13+
      'android.permission.CAMERA', // §6.6
      'android.permission.RECORD_AUDIO',
    ],
    adaptiveIcon: {
      foregroundImage: './assets/branding/adaptive-icon.png',
      backgroundColor: '#FFFFFF',
    },
    blockedPermissions: [
      // 백그라운드 위치 추적은 범위 밖입니다 (§6.4). 임장 궤적은 사용자가 알림으로
      // 확인할 수 있는 포그라운드 서비스(foregroundServiceType=location)로만 모읍니다.
      'android.permission.ACCESS_BACKGROUND_LOCATION',
    ],
  },

  plugins: [
    'expo-router',
    './plugins/with-installed-related-apps',
    'expo-secure-store',
    'expo-font',
    'expo-asset',
    'expo-image',
    'expo-web-browser', // 카카오·네이버 간편 로그인 인가 코드 수신용 (§14)
    '@react-native-community/datetimepicker', // 스터디 일정 등록·수정 날짜/시간 선택 (FE-012)

    // Mapbox maven 저장소를 android/build.gradle 에 주입합니다. 반드시 필요합니다.
    // Download token 은 폐기되었으므로 prop 을 넘기지 않습니다 (§5.3).
    '@rnmapbox/maps',

    [
      '@react-native-seoul/kakao-login',
      {
        // 네이티브 앱 키. kakao{KEY}://oauth 리다이렉트 스킴과 strings.xml 에 주입됩니다.
        kakaoAppKey: KAKAO_APP_KEY,
        // 필수. 생략하면 이 plugin 이 gradle.properties 의 android.kotlinVersion 을
        // 기본값 1.5.10 으로 덮어써서 Android 빌드가 깨집니다.
        // RN 0.86 의 gradle/libs.versions.toml 값과 맞춥니다.
        kotlinVersion: '2.1.20',
      },
    ],

    [
      'expo-location',
      {
        locationWhenInUsePermission:
          '주변 아파트를 지도에 표시하고, 임장 시작 시 대상 아파트 근처인지 확인하기 위해 위치 정보를 사용합니다.',
        // 임장 궤적은 화면을 꺼 둔 구간도 이어져야 해서 포그라운드 서비스로 수집합니다
        // (FOREGROUND_SERVICE·FOREGROUND_SERVICE_LOCATION 이 매니페스트에 붙습니다).
        // 알림이 떠 있는 동안에만 위치가 들어오므로 백그라운드 위치 권한은 계속 쓰지
        // 않습니다 — blockedPermissions 의 ACCESS_BACKGROUND_LOCATION 은 그대로 둡니다.
        isAndroidBackgroundLocationEnabled: false,
        isAndroidForegroundServiceEnabled: true,
      },
    ],

    [
      'expo-image-picker',
      {
        photosPermission: '임장 기록과 게시글에 사진을 첨부하기 위해 사진 접근 권한이 필요합니다.',
        cameraPermission: '임장 현장 사진을 촬영하기 위해 카메라 접근 권한이 필요합니다.',
      },
    ],

    // 임장 경로 요약 이미지를 갤러리에 저장하는 용도로만 씁니다 (FE-018).
    // 갤러리를 읽지 않으므로 granularPermissions 를 사진 하나로 좁혀 READ_MEDIA_VIDEO·
    // READ_MEDIA_AUDIO 가 매니페스트에 붙지 않게 합니다. 위치 메타데이터도 필요 없어
    // ACCESS_MEDIA_LOCATION 은 껴 두지 않습니다.
    [
      'expo-media-library',
      {
        photosPermission: '저장한 임장 경로 이미지를 확인하기 위해 사진 접근 권한이 필요합니다.',
        savePhotosPermission: '임장 경로 이미지를 갤러리에 저장하기 위해 권한이 필요합니다.',
        isAccessMediaLocationEnabled: false,
        granularPermissions: ['photo'],
      },
    ],

    [
      'expo-audio',
      {
        microphonePermission:
          '임장 현장에서 음성 메모를 녹음하고 텍스트로 변환하기 위해 마이크 접근 권한이 필요합니다.',
        recordAudioAndroid: true,
        enableBackgroundRecording: false,
        enableBackgroundPlayback: false,
      },
    ],

    [
      'expo-notifications',
      {
        color: '#FFFFFF',
        // FirebaseAdminFcmPushGateway와 같은 ID를 사용해야 앱 프로세스가 없는
        // 상태에서도 Android 시스템이 올바른 고중요도 채널로 표시할 수 있습니다.
        defaultChannel: ANDROID_NOTIFICATION_CHANNEL_ID,
      },
    ],

    [
      'expo-build-properties',
      {
        android: {
          // 로컬 HTTP는 development에서만 허용하고 preview/production은 평문 통신을 차단합니다.
          usesCleartextTraffic: !IS_RELEASE_BUILD,
          // Kakao Login SDK 모듈은 Kakao의 Maven 저장소에서 받아야 합니다.
          // android/는 prebuild로 재생성되므로 이 설정을 단일 설정 지점에 둡니다.
          extraMavenRepos: ['https://devrepo.kakao.com/nexus/content/groups/public/'],
        },
      },
    ],

    // JS 번들이 뜨는 즉시 _layout.tsx 에서 hideAsync() 로 곧바로 걷어내서
    // 네이티브 스플래시는 아주 잠깐만 보입니다. Android 12+ SplashScreen API는
    // 아이콘을 정사각형 세이프존(288dp) 안에 강제로 작게 넣는 관례가 있어서,
    // 아이콘을 넣으면 오히려 작고 어중간해 보입니다 — 그래서 아이콘 없이 흰
    // 배경만 두고, 로고 노출은 JS 스플래시(SplashScreen.tsx)의 페이드인
    // 애니메이션 하나로 처리합니다. 배경색은 JS 스플래시와 동일하게 맞춰서
    // 전환이 이어지는 것처럼 보이게 합니다.
    // image를 아예 생략하면 expo-splash-screen 플러그인이 styles.xml에
    // @drawable/splashscreen_logo 참조는 남기면서 실제 drawable 파일은 생성하지
    // 않는 버그가 있어(리소스 누락으로 빌드 깨짐), 투명 1x1 PNG를 대신 넣어둡니다.
    [
      'expo-splash-screen',
      {
        backgroundColor: '#FFFFFF',
        image: './assets/branding/transparent.png',
      },
    ],
  ],

  experiments: {
    typedRoutes: true, // expo-router 타입 생성 (§6.1)
  },

  updates: {
    url: `https://u.expo.dev/${EAS_PROJECT_ID}`,
  },
  runtimeVersion: {
    policy: 'fingerprint',
  },

  extra: {
    eas: { projectId: EAS_PROJECT_ID },
    buildProfile: BUILD_PROFILE,
  },
});
