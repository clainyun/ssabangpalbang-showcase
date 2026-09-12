# 싸방팔방 Android 초기 세팅 가이드

| | |
|---|---|
| 버전 | **v2.0** |
| 작성일 | 2026-07-25 |
| 성격 | **실행 가이드.** 결정은 여기서 하지 않습니다 |
| 결정 문서 | [ANDROID_STACK_DECISION.md](./ANDROID_STACK_DECISION.md) v2.0 |
| 배경 | [ANDROID_BACKGROUND.md](./ANDROID_BACKGROUND.md) |

> **v1.0에서 성격이 바뀌었습니다.** v1.0은 "생성 전에 결정할 것"을 나열한 **결정 전 문서**였습니다. 결정이 끝났으므로 v2.0은 **결정을 실행하는 절차**만 담습니다. 폐기된 v1.0 내용은 §8에 정리했습니다.

---

## 0. 전제

결정 문서 v2.0 기준입니다. 아래와 다르게 하려면 **먼저 결정 문서를 고치십시오.**

| 항목 | 값 |
|---|---|
| 생성 방식 | Expo (CNG) + development build |
| Expo SDK | **57** |
| React Native | **0.86** (SDK 57이 결정) |
| 앱 폴더 | `frontend/` |
| 네이티브 언어 | Kotlin (템플릿 기본값 유지) |
| `frontend/android` | **커밋하지 않음** |
| 패키지 매니저 | npm |

---

## 1. 사전 준비 (팀원 각자)

| 항목 | 버전 | 확인 |
|---|---|---|
| Node.js | **22 LTS (22.13.0 이상)** | `node -v` |
| JDK | **17 (Temurin)** — 백엔드와 동일하므로 추가 설치 불필요 | `java -version` |
| Android SDK | Android Studio 설치로 확보 | — |
| adb | `platform-tools`를 PATH에 추가 | `adb version` |

> RN 0.86의 Node 요구는 `^20.19.4 || ^22.13.0 || ^24.3.0 || >=25.0.0`입니다. **22 LTS를 쓰되 22.13.0 이상**이어야 합니다. v1.0의 "22.11.0 이상"은 잘못된 값이었습니다.

### Android Studio에서 받을 것

SDK Manager에서:

- Android SDK Platform **36**
- Android SDK Build-Tools **36.0.0**
- Android SDK Platform-Tools
- Android Emulator
- NDK (Expo가 요구하는 버전 — 첫 빌드 시 안내됨)
- CMake

에뮬레이터는 **API 36, Google Play 이미지** 권장.

환경변수:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"        # macOS
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

> **Android Studio를 주 IDE로 쓸 필요는 없습니다.** SDK/NDK 확보, AVD 관리, Logcat, Gradle 문제 추적에만 씁니다. 에디터는 개인 선택입니다 (결정 문서 §11).
>
> **`frontend/android`를 열지 마십시오.** 생성물이며 커밋되지 않습니다 (결정 문서 §8.3).

---

## 2. 프로젝트 생성

### 2.1 생성

```bash
cd S15P11A701
rm android/.gitkeep && rmdir android

npx create-expo-app@latest frontend --template default
cd frontend
npx expo install expo@^57.0.0 --fix
```

생성 후 `package.json`의 `expo` 버전이 `^57`인지, `react-native`가 `0.86.x`인지 확인합니다.

### 2.2 앱 식별자

`app.config.ts`:

| 항목 | 값 |
|---|---|
| `android.package` | `com.ssafy.ssabangpalbang` (백엔드 group과 일치) |
| `name` | 싸방팔방 |
| `slug` | `ssabangpalbang` |

### 2.3 `.gitignore` 갱신 (저장소 루트)

```gitignore
# React Native / Node
node_modules/
npm-debug.log
*.hprof
.expo/

# Expo prebuild 생성물 — 커밋하지 않음 (결정 문서 §8.3)
frontend/android/
frontend/ios/
```

이미 커버됨: `**/build/`, `local.properties`, `.idea/`, `*.keystore`, `*.jks`, `google-services.json`, `.env`, `.env.*`, `!.env.example`

### 2.4 저장소 설정 파일

**`.editorconfig`** — 이미 존재. JS/TS 명시 추가:

```ini
[*.{js,jsx,ts,tsx}]
indent_size = 2
```

**`.gitattributes`** — 이미 `* text=auto eol=lf`. 추가 불필요.

**`frontend/.prettierrc`**

```json
{ "endOfLine": "lf", "singleQuote": true, "arrowParens": "avoid" }
```

`endOfLine: "lf"`는 Windows 팀원의 CRLF 혼입을 막습니다. 필수입니다.

**`frontend/.nvmrc`** — `22`

**ESLint** — `eslint-config-expo`, **TypeScript** — `strict: true`

---

## 3. 라이브러리 설치

**`npx expo install`을 쓰십시오.** `npm install`은 SDK 호환 버전을 맞춰주지 않습니다.

```bash
cd frontend

# 지도
npx expo install @rnmapbox/maps

# 네비게이션 (create-expo-app default 템플릿에 포함될 수 있음)
npx expo install expo-router react-native-safe-area-context react-native-screens

# 상태
npm install @tanstack/react-query zustand

# 인증
npx expo install expo-secure-store
npx expo install @react-native-seoul/kakao-login

# 위치 · 센서
npx expo install expo-location expo-sensors

# 푸시
npx expo install expo-notifications

# 파일 · 이미지
npx expo install expo-image-picker expo-image-manipulator expo-image

# 애니메이션 (SDK 57 번들 버전으로 정렬)
npx expo install react-native-reanimated lottie-react-native

# 실시간 · 폼
npm install @stomp/stompjs react-hook-form

# 빌드 속성
npx expo install expo-build-properties expo-dev-client
```

설치 후 반드시:

```bash
npx expo-doctor@latest
```

> `@shopify/react-native-skia`는 스프라이트 B안 채택 시에만 추가합니다 (결정 문서 §7.1).

---

## 4. `app.config.ts` 설정

네이티브 설정은 **전부 여기서** 합니다. `android/` 파일을 직접 고치지 않습니다.

### 4.1 권한

| 권한 | 용도 | 비고 |
|---|---|---|
| `INTERNET` | — | 기본 포함 |
| `ACCESS_FINE_LOCATION` | 지도 · 임장 | 런타임 요청 필요 |
| `ACCESS_COARSE_LOCATION` | 위 폴백 | |
| `POST_NOTIFICATIONS` | 푸시 | Android 13+ 런타임 요청 |
| `CAMERA` | 임장 증빙 촬영 | |
| `READ_MEDIA_IMAGES` | 갤러리 | Android 13+ |

**백그라운드 위치는 요청하지 않습니다** (결정 문서 §6.4).

### 4.2 config plugin 목록

| plugin | 설정 |
|---|---|
| `expo-router` | — |
| `@rnmapbox/maps` | Public Token은 런타임 환경변수로 주입. **`RNMapboxMapsDownloadToken`을 설정하지 마십시오 — deprecated** (§5.3) |
| `expo-location` | `locationWhenInUsePermission` (한글 문구) |
| `expo-notifications` | 아이콘·색상 |
| `@react-native-seoul/kakao-login` | `kakaoAppKey` |
| `expo-build-properties` | Kakao SDK Maven 저장소, 개발용 cleartext 허용 |
| `expo-image-picker` | 카메라·갤러리 권한 문구 |
| `lottie-react-native` | — |

### 4.3 환경변수

```bash
# frontend/.env.local  (gitignore 대상)
EXPO_PUBLIC_MAPBOX_TOKEN=pk.xxxxx
EXPO_PUBLIC_API_BASE_URL=http://192.168.0.10:8080
EXPO_PUBLIC_WS_URL=ws://192.168.0.10:8080/ws
```

`frontend/.env.example`에 **키 이름만** 적어 커밋합니다 (`infra/.env.example`과 동일 패턴).

> **`react-native-config`를 쓰지 않습니다.** Expo는 `EXPO_PUBLIC_` 접두어 환경변수를 번들에 자동 포함합니다.
>
> **Mapbox Secret Token(`sk.`)은 필요 없습니다.** 결정 문서 §5.3 참조.

### 4.4 `google-services.json`

FCM에 필요하지만 `.gitignore` 대상입니다. **팀 공유 경로를 정해야 합니다** (결정 문서 §14 항목 3).

`app.config.ts`에서 `android.googleServicesFile`로 지정합니다.

---

## 5. 빌드와 실행

### 5.1 첫 development build

```bash
cd frontend
npx expo run:android          # prebuild + gradle 빌드 + 설치
```

이후 JS만 바뀌면 `npx expo start`로 충분합니다. **네이티브 의존성을 추가했을 때만 다시 빌드합니다.**

### 5.2 `expo prebuild` 주의

**SDK 57에서 기본 동작이 바뀌었습니다.**

```bash
npx expo prebuild              # android/, ios/를 삭제 후 재생성 (기본값)
npx expo prebuild --no-clean   # 기존 폴더에 변경만 적용
```

`android/`에 수동 수정을 넣어두면 **기본 `prebuild` 한 번에 사라집니다.** 그래서 커밋하지 않고, 필요한 수정은 custom config plugin(`frontend/plugins/`)으로 표현합니다.

### 5.3 EAS Build

```bash
npx eas-cli build --platform android --profile development
npx eas-cli build --platform android --profile preview      # 시연용 release APK
```

**무료 티어는 Android 15회/월입니다.** 매 커밋마다 돌리지 마십시오.

**시연용 release APK를 1주차 말에 한 번 만들어 S23에 설치해 보십시오.** debug에서 되던 것이 release에서 깨지는 대표 원인은 R8 난독화와 `__DEV__` 분기입니다.

---

## 6. 백엔드 연동

| 실행 환경 | 백엔드 주소 |
|---|---|
| Android 에뮬레이터 | `http://10.0.2.2:8080` (에뮬레이터에서 `localhost`는 에뮬레이터 자신) |
| **실기기 (같은 Wi-Fi)** | `http://<PC의 LAN IP>:8080`, 백엔드에 `server.address=0.0.0.0` 필요 |

- Metro는 8081을 씁니다. 백엔드 8080과 충돌하지 않습니다
- 실기기 디버깅: `adb reverse tcp:8081 tcp:8081`
- **Cleartext(HTTP) 허용** — Android 9+는 평문 HTTP를 차단합니다. `expo-build-properties`의 `android.usesCleartextTraffic`을 **개발 프로파일에만** 켜고 release에서는 끕니다

**검증**

```bash
adb shell curl http://<LAN IP>:8080/actuator/health
```

---

## 7. CI

`android/`를 커밋하지 않으므로 CI에서 Gradle 빌드를 직접 돌리지 않습니다.

**GitLab CI 최소 job**

```yaml
frontend-check:
  image: node:22
  cache:
    paths: [frontend/node_modules]
  script:
    - cd frontend
    - npm ci
    - npx tsc --noEmit
    - npx eslint .
```

APK 산출은 **EAS Build**로 처리합니다. CI에서 Android SDK 이미지를 관리하지 않아도 됩니다.

> Mapbox Secret Token이 폐기되었으므로 **GitLab CI/CD Variables에 등록할 지도 관련 비밀이 없습니다** (결정 문서 §5.3).

---

## 8. v1.0에서 폐기된 내용

| v1.0 내용 | 상태 | 사유 |
|---|---|---|
| §1 "Bare RN CLI vs Expo 결정 필요" | 폐기 | **Expo로 확정** (결정 문서 §4.5) |
| §1 "국내 지도 SDK를 쓸 계획이면 Bare 권장" | 폐기 | Mapbox는 외부 제약이며 config plugin 제공 |
| §2 "React Native 0.85.x" 헤더 / 본문 "0.84.1" / 권장 "0.85.3" | 폐기 | 한 절에 세 버전이 혼재했음. **SDK 57 / RN 0.86으로 확정** |
| §2 "Node 22.11.0 이상" | **정정** | RN 0.86 요구는 `^22.13.0` 계열. §1 참조 |
| §2 Gradle·Kotlin·NDK·buildTools 개별 지정 | 폐기 | **Expo SDK가 결정합니다.** 직접 고정하면 SDK와 어긋남 |
| §2 "Android Studio에서 `android/` 폴더를 열 것" | 폐기 | `android/`는 생성물 (§5.2) |
| §3 네이티브 Java 변환 | 폐기 | **Kotlin 유지 확정** (결정 문서 §8.1) |
| §4 "폴더 위치 결정 필요" | 폐기 | **`frontend/` 확정** |
| §5.6 라이브러리 후보 | **전면 교체** | 아래 표 |
| §5.7 CI `./gradlew assembleDebug` | 폐기 | `android/` 미커밋 → EAS Build (§7) |
| §6 다음 단계 1~3 | 폐기 | 모두 결정 완료 |

### 라이브러리 교체 내역

| 용도 | v1.0 후보 | v2.0 결정 | 사유 |
|---|---|---|---|
| 화면 전환 | `@react-navigation/*` | **`expo-router`** | 파일 기반 라우팅, 딥링크 자동 생성 |
| 토큰 저장 | `react-native-keychain` | **`expo-secure-store`** | Expo 1급 지원 |
| 지도 | "네이버/카카오 또는 `react-native-maps`" | **`@rnmapbox/maps`** | Mapbox가 외부 제약 |
| 푸시 | `@react-native-firebase/*` | **`expo-notifications`** | 서버가 FCM 직발송이므로 네이티브 토큰만 필요 |
| 이미지 선택 | `react-native-image-picker` | **`expo-image-picker`** | Expo 1급 |
| 이미지 표시 | `react-native-fast-image` | **`expo-image`** | Expo 1급, 캐시 내장 |
| 환경변수 | `react-native-config` | **`EXPO_PUBLIC_` 접두어** | Expo 내장 |
| HTTP | `axios` | **미결정** | TanStack Query + `fetch`로 충분할 수 있음. 첫 API 훅 작성 시 결정 |
| 서버 상태 | `@tanstack/react-query` | 유지 | — |
| 클라이언트 상태 | `zustand` | 유지 | — |
| 실시간 | `@stomp/stompjs` | 유지 | — |
| 폼 | `react-hook-form` + `zod` | 유지 | — |

---

## 9. 착수 체크리스트

**1일차 오전**

- [ ] `android/.gitkeep` 제거, `frontend/` 생성 (§2.1)
- [ ] Expo SDK 57 고정 확인 (`npx expo-doctor`)
- [ ] `.gitignore` · Prettier · ESLint · `.nvmrc` 설정 (§2.3~2.4)
- [ ] Mapbox 팀 공용 계정 + Public Token 발급, 앱 제한 설정
- [ ] `frontend/.env.example` 커밋
- [ ] `docs/LOCAL_DEVELOPMENT.md`의 프로젝트 구조에서 `android` → `frontend` 수정

**1일차 검증 — 결정 문서 §13 스파이크를 그대로 따릅니다.** 순서가 중요합니다.

0. **S23에서 대상 아파트 단지 Mapbox 렌더 → 한글 라벨·POI 확인** ← 여기서 막히면 아래 진행 안 함
1. 지도 마운트 + `adb logcat`에 `getDelegate` SoftException 없는지 확인
2. 지도 위 스프라이트 3개 + 카메라 이동 프레임 측정
3. 카카오 로그인 1회 성공 (debug 키해시 등록 필요)
4. 흔들기 감지 + Dev Menu 억제
5. 실기기에서 백엔드 연결 (§6)
6. 실제 Lottie `.json` 렌더

**1주차 말**

- [ ] release APK 생성 후 S23 설치 검증 (§5.3)
