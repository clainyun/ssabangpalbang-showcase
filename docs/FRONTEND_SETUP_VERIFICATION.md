# frontend 초기 세팅 · 라이브러리 충돌 검증

| | |
|---|---|
| 작성일 | 2026-07-25 |
| 대상 | `frontend/` (Expo SDK 57 / RN 0.86) |
| 근거 문서 | [ANDROID_STACK_DECISION.md](./ANDROID_STACK_DECISION.md) v2.1 |
| 검증 방법 | npm registry 실측 + Linux 샌드박스 실제 설치(843 패키지) + `expo config --type prebuild` + `tsc --noEmit` + `eslint` |
| 결론 | **차단 수준 충돌 0건.** 문서 정정 필요 4건, 조치 필요 2건 |

> **`node_modules` 는 커밋되지 않았습니다.** 검증은 샌드박스(Linux)에서 했으므로 macOS 에서 `cd frontend && npm install` 을 직접 실행하십시오.

---

## 1. 검증 결과 요약

| 검사 | 결과 |
|---|---|
| npm 의존성 해석 (`npm install`) | ✅ 성공, 843 패키지, ERESOLVE 실패 0건 |
| SDK 57 번들 버전 대조 (`expo install --check` 동등) | ✅ 불일치 0건 |
| 설치 트리 전체 peerDependency 검증 | ⚠️ 위반 1건 (무해 — §3.3) |
| 싱글턴 중복 설치 (react / RN / reanimated / worklets / gesture-handler / screens / safe-area / expo-modules-core) | ✅ 중복 0건 |
| Android minSdk / compileSdk / Kotlin 충돌 | ✅ 충돌 0건 (§4) |
| config plugin 해석 (`expo config --type prebuild`) | ✅ 9개 전부 해석, 경고 0건 |
| `tsc --noEmit` | ✅ 0 에러 |
| `eslint .` | ✅ 0 에러 |

---

## 2. 확정된 버전 (실측)

RN 0.86.0 의 `gradle/libs.versions.toml` 및 `expo@57.0.8/bundledNativeModules.json` 실측값입니다.

| 항목 | 값 | 비고 |
|---|---|---|
| expo | 57.0.8 | dist-tag `latest` |
| react-native | 0.86.0 | SDK 57 번들 |
| react / react-dom | 19.2.3 | **문서에는 19.2 로만 기재** |
| typescript | 6.0.3 | **TS 6.** 문서에 버전 언급 없음 |
| reanimated / worklets | 4.5.0 / 0.10.0 | 문서 §4.2(4) 일치 |
| gesture-handler | 2.32.0 | 문서 §4.2(4) 일치 |
| expo-router | 57.0.8 | |
| lottie-react-native | 7.3.8 | |
| **@rnmapbox/maps** | **10.3.5** | **문서는 10.3.1 — §3.1 참조** |
| minSdk / targetSdk / compileSdk | 24 / **36** / 36 | 문서 §4.2(2) 의 targetSdk 36 확인 |
| NDK / AGP / Kotlin | 27.1.12297006 / 8.12.0 / 2.1.20 | |

---

## 3. 발견 사항

### 3.1 ✅ 해소 — R2 (rnmapbox #4194 Android 16 프리즈) 는 이미 릴리스되었습니다

**문서 §5.2 의 결정을 갱신해야 합니다.** 문서는 "PR #4228 이 머지됐으나 10.3.1 에 미포함, `main` 에만 존재"라고 판단해 **`main` 커밋 고정 또는 patch-package** 를 대응으로 걸어두었습니다.

실측 결과 **10.3.2 (2026-07-05)** 와 **10.3.5 (2026-07-22)** 가 이미 나와 있고, 패키지 소스에서 수정이 확인됩니다.

| 버전 | `getDelegate()` 를 가진 android 소스 파일 수 |
|---|---|
| 10.3.1 | 8 |
| 10.3.2 | 36 |
| 10.3.5 | 36 |

10.3.2 에서 추가된 **28개** ViewManager (`RNMBXCameraManager`, `RNMBXShapeSourceManager`, `RNMBXCircleLayerManager`, `RNMBXMarkerViewContentManager` …) 는 PR #4228 의 대상과 일치합니다.

**따라서 `@rnmapbox/maps@10.3.5` 를 설치했습니다.** `main` 커밋 고정도, patch-package 도 불필요합니다.

**단, 스파이크 1번은 그대로 수행하십시오.** 소스에 수정이 들어간 것과 Galaxy S23 / Android 16 에서 프리즈가 사라진 것은 다른 명제입니다. `adb logcat` 에 `ViewManager ... must override getDelegate` 가 찍히는지 확인하는 절차는 유지합니다. 또한 10.3.5 는 SDK 57(06-30)보다 **뒤에** 나왔으므로 문서 §4.3 의 "검증 공백" 리스크가 10.3.1 기준보다 줄어들었습니다.

> #4192(`onCameraChanged` 폭주), #4206(MarkerView 소실), #4213, #4203 이 10.3.5 에서 고쳐졌는지는 소스만으로 판정할 수 없습니다. **문서 §5.2 의 결정 4·5(MarkerView 회피, 스로틀 선제 적용)는 유지하십시오.**

### 3.2 ⚠️ 조치 완료 — `react-dom` 을 명시적으로 고정해야 합니다

`react-dom` 은 `expo-router` 의 **optional peer** 이지만 npm 은 optional peer 도 설치를 시도합니다. 범위 지정이 없으면 최신 `19.2.8` 이 들어오고, 그것의 peer 인 `react@^19.2.8` 이 SDK 가 고정한 `react@19.2.3` 과 어긋납니다.

```
npm warn ERESOLVE overriding peer dependency
  edge: peer react@^19.2.8 from react-dom@19.2.8
  dep:  react@19.2.3  (whileInstalling: expo-router@57.0.8)
```

경고이므로 설치는 되지만 React 사본 불일치의 씨앗입니다. **`package.json` 에 `"react-dom": "19.2.3"` 을 명시**해 해소했습니다 (재검증 시 경고 0건).

### 3.3 ⚠️ 무해 — peer 위반 1건

```
fdir@6.5.0  →  picomatch@2.3.2  (요구: ^3 || ^4)
```

빌드 툴체인 내부의 호이스팅 산물입니다. RN 런타임 경로와 무관하고 `@expo/metro-config` 가 자체 `picomatch@4` 를 갖고 있습니다. **조치 불필요.**

### 3.4 ❌ 문서 정정 — Lottie 는 config plugin 이 없습니다

문서 §4.5(2) 는 "`lottie-react-native` … config plugin 을 제공합니다", §8.2 는 "Lottie | config plugin" 으로 기재하고 있습니다. **`lottie-react-native@7.3.8` 에는 `app.plugin.js` 도 `expo` 필드도 없습니다.**

문서대로 `plugins` 배열에 넣으면 prebuild 가 **실패**합니다 (실측):

```
PluginError: Unexpected token 'typeof'
  at resolveConfigPluginFunctionWithInfo (@expo/config-plugins/.../plugin-resolver.js)
```

Android 에서는 오토링킹만으로 충분하므로 **`app.config.ts` 의 `plugins` 에 넣지 않았습니다.** 별도 설정 없이 동작합니다.

### 3.5 ❌ 문서 정정 — 카카오 Maven 저장소를 직접 선언할 필요가 없습니다

문서 §6.3 은 "Kakao SDK Maven 저장소 선언 → `expo-build-properties` plugin" 을 필요 작업으로 올려두었습니다. **`@react-native-seoul/kakao-login@5.4.2` 가 자기 `android/build.gradle` 에서 직접 주입합니다:**

```gradle
rootProject.allprojects {
  repositories {
    maven { url 'https://devrepo.kakao.com/nexus/content/groups/public/' }
  }
}
```

`expo-build-properties` 의 `extraMavenRepos` 는 사용하지 않았습니다. **§6.3 의 나머지 작업(키해시 콘솔 등록, config plugin `kakaoAppKey`, development build 재생성)은 그대로 유효합니다.**

### 3.6 ❌ 문서 정정(간접) — `edgeToEdgeEnabled` 옵션은 SDK 57 에서 제거되었습니다

문서 §4.2(2)의 취지대로 `android.edgeToEdgeEnabled: true` 를 넣었더니 경고가 났습니다:

```
» android: EDGE_TO_EDGE_PLUGIN: `edgeToEdgeEnabled` customization is no longer
  available - Android 16 makes edge-to-edge mandatory. Remove the entry.
```

같은 이유로 **`newArchEnabled` 도 SDK 57 의 `ExpoConfig` 타입에서 사라졌습니다** (RN 0.82 부터 비활성화 옵션 제거 — 문서 §8.1 서술과 일치). 둘 다 제거했고 주석으로 사유를 남겼습니다.

### 3.7 ⚠️ 주의 — Reanimated babel 플러그인을 **수동으로 넣지 마십시오**

`babel-preset-expo@57.0.4` 가 `react-native-worklets/plugin` 을 **자동 주입**합니다 (`build/configs/expo.js:109-113`). 그래서 `babel.config.js` 를 만들지 않았습니다. 손으로 추가하면 이중 주입으로 깨집니다.

### 3.8 ⚠️ 주의 — `app.config.ts` 에서 node 빌트인 사용 금지

`expo/tsconfig.base` 가 `moduleResolution: "bundler"` + `customConditions: ["react-native"]` 이므로 `app.config.ts` 에서 `node:fs` / `node:path` / `__dirname` 이 타입 해석에 실패합니다(TS2591/TS2304). 처음에 `google-services.json` 존재 여부를 `fs.existsSync` 로 확인하려다 막혔고, **환경변수 `GOOGLE_SERVICES_JSON` 유무로만 분기**하도록 바꿨습니다.

### 3.9 참고 — WebP 애니메이션은 기본 비활성

`expo-template-bare-minimum@57.0.10` 의 `gradle.properties` 기본값입니다.

```properties
expo.webp.enabled=true
expo.webp.animated=false
```

§7.1 의 스프라이트 시트는 **정지 WebP** 이므로 그대로 동작합니다. 애니메이션 WebP 를 쓰려면 `expo-build-properties` 로 켜야 합니다.

---

## 4. Android 빌드 설정 충돌 검사

네이티브 모듈 전체가 `rootProject.ext` 에서 값을 읽으므로 개별 충돌은 없습니다. 최댓값(floor) 확인:

| 모듈 | minSdk 기본값 | SDK 57 값(24) 대비 |
|---|---|---|
| react-native-gesture-handler | **24** | 정확히 일치 — 여유 없음 |
| @rnmapbox/maps | 21 | 여유 |
| lottie-react-native | 21 | 여유 |
| react-native-safe-area-context | 16 | 여유 |
| @react-native-seoul/kakao-login | ext 위임 | — |

Kotlin 2.1.20 은 Expo 의 지원 상한(`>= 2.3.0` 에서 거부)을 넘지 않습니다. **`minSdkVersion` 을 24 아래로 내리지 마십시오** — gesture-handler 가 깨집니다.

---

## 5. 생성된 구조

```
frontend/
├── app.config.ts              네이티브 설정 단일 지점, config plugin 9개
├── package.json               라이브러리 전체 (§1.2 13개 + 필수 peer)
├── tsconfig.json              strict + noUncheckedIndexedAccess + @/* → src/*
├── eslint.config.js           eslint-config-expo (flat)
├── .prettierrc                endOfLine: "lf" (§11)
├── .env.example               Mapbox / 카카오 / API / WS / FCM 키 이름
├── .gitignore                 /android, /ios, google-services.json 등
├── app/                       expo-router (§6.1 라우트 초안 그대로)
│   ├── _layout.tsx            GestureHandlerRoot + SafeArea + QueryClient
│   ├── index.tsx              SecureStore 복구 후 분기
│   ├── +not-found.tsx
│   ├── (auth)/                _layout(게이팅) · login · callback
│   └── (app)/
│       ├── _layout.tsx        게이팅 + Stack
│       ├── (tabs)/            index(지도) · community · study · chat · my
│       ├── apartment/[id].tsx
│       ├── field/[sessionId].tsx
│       ├── post/[id].tsx
│       ├── study/[id].tsx
│       ├── chat/[roomId].tsx
│       └── chatbot.tsx
├── src/
│   ├── lib/                   env · tokenStorage(SecureStore) · queryClient · mapbox
│   ├── store/                 authStore · characterStore (저빈도 상태만)
│   ├── api/                   (빈 폴더)
│   ├── components/            (빈 폴더)
│   └── features/              map · field · post · chat · study · chatbot (빈 폴더)
└── assets/                    sprites/ · lottie/ · images/
```

`babel.config.js` · `metro.config.js` 는 **의도적으로 만들지 않았습니다** (§3.7).
`plugins/` (custom config plugin, §8.3) 는 필요해질 때 만드십시오 — 빈 폴더는 두지 않았습니다.

---

## 6. 다음 할 일

**즉시 (착수 차단)**

1. `cd frontend && npm install` — macOS 에서 직접. `package-lock.json` 이 생성되면 커밋하십시오 (§11)
2. `frontend/.env.local` 작성 — `.env.example` 복사 후 Mapbox Public Token 채우기 (§5.3 즉시 실행 1~3)
3. `npx expo prebuild --platform android` → `npx expo run:android`

**스파이크 전 (§13)**

4. 카카오 개발자 콘솔에서 네이티브 앱 키 발급 → `EXPO_PUBLIC_KAKAO_APP_KEY`, **debug/release 키해시 둘 다 등록** (R5)
5. `app/google-services.json` 확보 후 `GOOGLE_SERVICES_JSON=./app/google-services.json` 설정 (§14 항목 3 — 공유 방법 미결정)
6. `EXPO_PUBLIC_API_BASE_URL` / `EXPO_PUBLIC_WS_URL` 을 개발 PC LAN IP 로 수정 (스파이크 5)

**문서 갱신**

7. `ANDROID_STACK_DECISION.md` §5.2 를 10.3.5 기준으로 갱신 (§3.1) — 이 항목이 가장 중요합니다
8. §4.5(2)·§8.2 의 Lottie config plugin 기재 삭제 (§3.4)
9. §6.3 의 "Kakao Maven 저장소 선언" 작업 삭제 (§3.5)

**release 전**

10. `app.config.ts` 의 `usesCleartextTraffic: true` 를 `false` 로 되돌리기
11. 1주차 말에 release APK 1회 생성 (R8)
