# Android 스택 결정

| | |
|---|---|
| 버전 | **v2.1** |
| 작성일 | 2026-07-25 |
| 상태 | **확정.** 미결정 7건 잔존 (높음 3건 / 중간 3건 / 낮음 1건 — §14). **착수 전 인간 판단이 필요한 것은 1건**(§14 항목 1) |
| 대체 대상 | v2.0 (2026-07-25) → 스프라이트 구현 방식 확정. v1.0 정정 내역은 §15 |
| 배경·설명 | [ANDROID_BACKGROUND.md](./ANDROID_BACKGROUND.md) |
| 리뷰 기록 | [ANDROID_STACK_DECISION_REVIEW.md](./ANDROID_STACK_DECISION_REVIEW.md) |

> **읽는 법** — 이 문서는 **결정과 근거만** 담습니다. "왜 Flutter가 아닌가", "TurboModule이 무엇인가" 같은 설명은 배경 문서로 옮겼습니다.
>
> **§13(스파이크)을 먼저 읽으십시오.** 이 문서의 결정 중 3건은 1~2일차 실측으로 확정 또는 폴백됩니다.

---

## 1. 결정 요약

### 1.1 플랫폼

| 항목 | 결정 | 근거 |
|---|---|---|
| 프레임워크 | **React Native** | §3 |
| **Expo SDK** | **57** (2026-06-30) | §4 |
| **React Native** | **0.86** (SDK 57 번들) | §4 |
| React | 19.2 | SDK 57 번들 |
| 생성 방식 | **Expo prebuild + development build** | §4.5 |
| 앱 코드 언어 | **TypeScript** | — |
| 네이티브 레이어 언어 | **Kotlin** (템플릿 기본값) | §8.1 |
| 패키지 매니저 | **npm** | 저장소 통일. Gradle Wrapper 관례와 동일하게 lockfile 커밋 |

### 1.2 라이브러리

| 영역 | 결정 | 절 |
|---|---|---|
| 지도 | `@rnmapbox/maps` **10.3.1** (조건부 — §5.2) | §5 |
| **네비게이션** | **`expo-router`** (파일 기반) | §6.1 |
| **서버 상태** | **TanStack Query** | §6.2 |
| **클라이언트 전역 상태** | **zustand** | §6.2 |
| **인증 토큰 저장** | **`expo-secure-store`** | §6.3 |
| **소셜 로그인** | **`@react-native-seoul/kakao-login` 5.x** | §6.3 |
| **위치** | **`expo-location`** | §6.4 |
| **푸시** | **`expo-notifications`** (FCM 직결) | §6.5 |
| **이미지 선택·압축** | **`expo-image-picker` + `expo-image-manipulator`** | §6.6 |
| **이미지 표시·캐시** | **`expo-image`** | §6.6 |
| **실시간** | **`@stomp/stompjs`** (WebSocket) | §6.7 |
| **폼** | **`react-hook-form`** | §6.8 |
| 애니메이션 런타임 | `react-native-reanimated` 4.5 (SDK 57 번들) | §7 |
| 캐릭터 | **스프라이트 시트** | §7.1 |
| **스프라이트 구현** | **A안 — Image + Reanimated** (Skia는 폴백) | §7.1 |
| UI 애니메이션 | `lottie-react-native` | §7.2 |

### 1.3 환경

| 항목 | 결정 |
|---|---|
| 앱 폴더 | **`frontend/`** (§9) |
| `frontend/android` 커밋 | **커밋하지 않음** (§8.3 — v1.0에서 뒤집힘) |
| 주 IDE | 개인 선택. 저장소 규약만 강제 (§11) |
| 시연 기기 | Galaxy S23 (SM-S911), Android 16 / API 36 |
| 동시 표시 캐릭터 | 최대 3개 |
| 개발 기간 | 2~3주 |

---

## 2. 전제와 제약

### 2.1 외부에서 주어진 제약

| # | 제약 | 성격 | 우리가 정할 수 있는가 |
|---|---|---|---|
| 1 | **Mapbox 사용** | 외부 제약 | ❌ 아니오 — §5.1 |
| 2 | 시연 기기 Galaxy S23 | 외부 제약 | ❌ |
| 3 | 개발 기간 2~3주 | 외부 제약 | ❌ |
| 4 | 흔들기(Shake) 감지 | 기능 요구 | ❌ |
| 5 | 캐릭터 8방향 × 37프레임 스프라이트, 지도 위 중첩 | 기능 요구 | 에셋 스펙은 조정 가능 |
| 6 | 버튼 등 UI 요소별 상호작용 애니메이션 | 기능 요구 | 구현 수단은 선택 가능 |

> **제약 1이 이 문서에서 가장 큰 리스크입니다.** 기술 스택 결정이 아니라 **제품 성립 여부**의 문제이므로 §5.1과 §13에서 별도로 다룹니다.

### 2.2 앱의 실제 범위

`backend/src/main/resources/db/migration/V1__init_schema.sql` 기준 33개 테이블.

| 도메인 | 대표 테이블 | 클라이언트 요구 | 결정 |
|---|---|---|---|
| 인증 | `member`, `social_account` | 소셜 로그인, 토큰 보관 | §6.3 |
| 푸시 | `fcm_token`, `notification` | FCM 토큰 발급·등록 | §6.5 |
| 실시간 | `chat_message`, `chat_read_status` | WebSocket, 읽음 처리 | §6.7 |
| 파일 | `file_meta`, `post_attachment`, `report_evidence` | 이미지 선택·압축·업로드 | §6.6 |
| 커뮤니티 | `post`, `post_comment`, `post_like`, `follow` | 목록·무한스크롤 | §6.2 |
| 부동산 | `apartment`, `apartment_transaction`, `apartment_favorite` | 지도 + 상세 | §5 |
| 스터디 | `study`, `study_notice`, `study_application`, `study_member`, `schedule` | 목록·폼 | §6.8 |
| 임장 | `field_session`, `field_participant`, `checklist`, `checklist_item`, `field_record`, `checklist_answer` | 위치 추적, 체크리스트 폼 | §6.4, §6.8 |
| 리포트 | `report`, `report_evidence`, `report_favorite` | 이미지 첨부 | §6.6 |
| AI | `chatbot_conversation`, `chatbot_message`, `apartment_rag_document` | 대화 UI | §6.2 |

**화면 수는 최소 20개, 탭 + 스택 중첩 + 인증 게이팅 구조입니다.** 이것이 §6.1(네비게이션)을 지도·캐릭터보다 먼저 결정해야 하는 이유입니다.

---

## 3. 프레임워크: React Native

### 결정

**React Native.** Flutter와 네이티브 Android를 배제합니다.

### 실제 결정 근거 (가중치 순)

**(1) 팀 경험 재사용 + 2~3주 일정 — 지배적 사유**

팀에 React 경험이 있고 Android 네이티브 경험자는 충분하지 않습니다. 2~3주에 §2.2의 20개 화면을 만들어야 합니다. 학습 곡선이 결정을 지배합니다.

**(2) 지도 위 오버레이 구조에서 구조적 불리함이 없음**

지도(MapView)와 캐릭터가 **같은 Android View 계층의 형제**라 겹쳐 그리는 데 추가 합성 비용이 없습니다. Flutter는 Mapbox를 PlatformView로 끼워 넣어야 하고 그 위에 위젯을 올리면 매 프레임 합성 단계가 추가됩니다.

> **다만 이 근거를 과대평가하지 않습니다.** Flutter의 PlatformView 성능 이슈([flutter#167547](https://github.com/flutter/flutter/issues/167547))는 실재하고 여전히 open이지만, 보고 환경이 **Flutter 3.24.5 / 3.27, 2025년 4월**입니다. Android API 34+ / Vulkan 기기에서 **HCPP**가 오버헤드를 크게 줄였고 S23은 조건을 만족합니다. 상세는 [배경 문서 §1](./ANDROID_BACKGROUND.md).
>
> **정직하게 말하면 이 항목은 "RN이 유리"가 아니라 "RN에는 이 리스크 자체가 없다"입니다.** 결정을 뒤집을 만큼의 근거는 (1)입니다.

**(3) Lottie 렌더 정확도**

`lottie-react-native`는 Airbnb 공식 네이티브 라이브러리(`lottie-android`) 래퍼입니다. Flutter의 `lottie`는 순수 Dart 재구현 비공식 포트입니다.

### 네이티브 Android를 배제한 이유

UI 생산성입니다. Java를 유지하면 XML + View 시스템(명령형)이고, Compose를 쓰면 Kotlin 전환 + Android SDK 학습이 필요해 **"Java 유지"라는 원래 선호가 어차피 깨집니다.** 그 시점에 네이티브를 고를 유인이 사라집니다. 상세는 [배경 문서 §2](./ANDROID_BACKGROUND.md).

---

## 4. Expo SDK 57 / React Native 0.86

### 4.1 SDK가 상위 결정입니다

> **v1.0의 오류 정정** — v1.0은 §7에서 "RN 0.84.1 확정", §8에서 "Expo 확정"을 각각 내렸습니다. **두 결정은 양립하지 않습니다.**

Expo에서는 **RN 버전을 직접 고르지 않습니다.** SDK를 고르면 RN 버전이 따라옵니다. `npx expo install`이 SDK에 맞는 버전을 강제하는 것도 같은 이유입니다.

| Expo SDK | 릴리스 | 번들 RN |
|---|---|---|
| 55 | — | 0.83 |
| 56 | 2026-05-21 | 0.85 |
| **57** | **2026-06-30** | **0.86** |

**RN 0.84를 싣는 안정 SDK는 없습니다.** SDK 55 → 56에서 0.83 → 0.85로 건너뛰었고, 0.84는 `expo@canary`에만 존재했습니다. canary는 2~3주 일정의 선택지가 아닙니다.

따라서 v1.0 §7의 "0.85 미검증이므로 0.84로 하향" 논증은 **성립 자체가 불가능**했습니다.

### 4.2 SDK 57을 고른 이유

**(1) `@rnmapbox/maps`가 상한을 걸지 않습니다 — 결정 기준**

지도가 핵심이므로 지도 라이브러리가 허용하는 최대 버전을 택했습니다.

`@rnmapbox/maps@10.3.1` 매니페스트 실측:

```json
"peerDependencies": { "react-native": ">=0.79" }
```

**상한이 없습니다.** 그리고 v10.3.1은 [PR #4200](https://github.com/rnmapbox/maps/pull/4200)에서 **"RN 0.84+ bridgeless 모드"** 크래시를 고쳤습니다 — 0.85·0.86을 포함하는 범위입니다.

**(2) RN 0.86의 edge-to-edge 수정이 이 앱 구조에 직접 필요합니다**

`targetSdkVersion 36`이므로 **Android 15+ edge-to-edge가 강제 적용**되고, 시연 기기는 Android 16입니다. RN 0.86은 이 상태에서의 문제들을 고쳤습니다.

- `measureInWindow`가 **올바른 좌표 반환**
- `Dimensions` window 값 정확도
- `StatusBar` / `Modal` 상호작용

이 앱의 핵심 화면은 **전체화면 지도 위에 캐릭터를 화면 좌표로 배치**하는 구조입니다. 좌표 계산 오차는 정확히 여기를 때립니다. 0.84/0.85를 고르면 이 버그를 안고 갑니다.

**(3) 0.85 → 0.86은 breaking change가 없습니다**

RN 0.86은 "no user-facing breaking changes"를 목표로 한 두 번째 릴리스이며, Expo도 SDK 57을 "가장 쉬운 업그레이드"로 명시했습니다.

**(4) SDK 57 번들 라이브러리가 최신입니다**

Reanimated 4.3 → **4.5**, worklets 0.8 → **0.10**, gesture-handler 2.31 → **2.32**. 캐릭터 애니메이션을 UI 스레드에서 돌리는 이 프로젝트에 직접 이득입니다.

### 4.3 인정하는 리스크

| 리스크 | 내용 | 완화 |
|---|---|---|
| **검증 공백** | rnmapbox 10.3.1(**05-16**)은 RN 0.86(**06-11**)·SDK 57(**06-30**)보다 **먼저** 나왔습니다. 이 조합은 메인테이너가 테스트한 적이 없습니다 | §13 스파이크 1번에서 **가장 먼저** 확인 |
| 생태계 지연 | SDK 57이 나온 지 한 달 미만 | 폴백 경로 아래에 명시 |

### 4.4 폴백 조건

**스파이크에서 아래가 발생하면 SDK 56(RN 0.85)로 즉시 하향합니다.**

- rnmapbox 빌드 실패 또는 지도 렌더 실패
- Reanimated / gesture-handler와 rnmapbox 충돌

하향 절차는 `npx expo install expo@^56.0.0 --fix` 후 재빌드입니다. **1일차에 판단하면 비용이 반나절 이하입니다. 3일차에 판단하면 며칠입니다.**

SDK 55(RN 0.83)까지는 내려가지 않습니다. 그 아래로 가야 할 정도면 Expo 자체를 재검토합니다.

### 4.5 Expo를 고른 이유

**(1) 네이티브 설정 지점 집중**

§6의 라이브러리 중 **소셜 로그인·푸시·위치·이미지**가 전부 네이티브 설정을 요구합니다. Bare에서는 팀원 각자가 `build.gradle`, `AndroidManifest.xml`, `MainApplication.kt`를 손봐야 하고 여기서 막히는 사람이 반드시 나옵니다. Expo에서는 `app.config.ts` 한 곳입니다.

> **v1.0 정정** — v1.0은 이 근거의 핵심으로 "Mapbox Secret Token 설정 지점이 3곳 → 1곳"을 들었습니다. **Secret Token 자체가 폐기되어(§5.3) 이 항목은 소멸했습니다.** 그럼에도 위 4개 라이브러리 때문에 결론은 유지됩니다.

**(2) 필요한 라이브러리가 전부 Expo를 지원합니다**

§1.2의 13개 중 Expo에서 못 쓰는 것이 없습니다. `expo-*` 계열 6개는 1급 지원이고, `@rnmapbox/maps`·`lottie-react-native`·`@react-native-seoul/kakao-login`은 config plugin을 제공합니다.

**(3) 버전 정합성**

`npx expo install`이 SDK 호환 버전을 자동 선택합니다. 13개 라이브러리를 수동 대조하지 않습니다.

**(4) 팀 환경 편차 우회**

Mac/Windows 혼재 시 EAS Build(무료 티어 **Android 15회/월**)로 로컬 Android SDK 없이 APK를 받을 수 있습니다.

### 4.6 Expo Go는 사용하지 않습니다 (사용할 수 없습니다)

`@rnmapbox/maps`, 카카오 로그인, FCM이 모두 커스텀 네이티브 코드를 요구합니다. **development build가 필수**이며, 이는 Bare를 골랐어도 동일합니다. Expo의 "설치 없이 즉시 실행" 장점은 처음부터 해당되지 않습니다.

---

## 5. 지도

### 5.1 Mapbox는 검증되지 않은 외부 제약입니다

Mapbox는 우리가 고른 것이 아니라 **주어진 제약**(§2.1)입니다. 그리고 이 제약에는 **문서화되지 않은 제품 리스크**가 있습니다.

- Mapbox 기본 스타일은 **OSM 기반**입니다. 한국 POI 밀도와 한글 라벨 품질이 네이버·카카오맵과 차이가 큽니다
- 앱 도메인이 **아파트·실거래가·임장**입니다. 단지명·동·주변시설 라벨이 부정확하면 **제품이 성립하지 않습니다**
- 시연 시나리오가 특정 단지 위에서 진행됩니다

**이 리스크는 스택 결정으로 완화할 수 없습니다.** §13 스파이크의 **0번 항목**으로 두어 다른 무엇보다 먼저 확인합니다.

**재검토 트리거** — 스파이크 0번에서 대상 단지의 한글 라벨·POI가 시연 가능한 수준이 아니면, 제약을 준 주체에게 **에스컬레이션**합니다. 이때의 대안은 네이버지도 SDK 또는 커스텀 타일 오버레이이며, 둘 다 §4·§6의 결정 상당수를 다시 씁니다.

### 5.2 `@rnmapbox/maps` 10.3.1 — 알려진 Android 리스크

**⚠️ 시연 기기(Android 16)에 직접 해당하는 미해결 이슈가 있습니다.**

| 이슈 | 내용 | 상태 | 이 프로젝트 영향 |
|---|---|---|---|
| **[#4194](https://github.com/rnmapbox/maps/issues/4194)** | **Android 16에서 앱 완전 프리즈.** 27개 ViewManager(`RNMBXCamera`, `RNMBXShapeSource`, `RNMBXCircleLayer`, `RNMBXMarkerViewContent` 등)가 `getDelegate()`를 오버라이드하지 않아 Fabric이 `ReactNoCrashSoftException`을 던지고 리플렉션 폴백. 하드 크래시가 아니라 **무반응 UI**. Galaxy S25 / Android 16에서 확인 | **[PR #4228](https://github.com/rnmapbox/maps/pull/4228) 머지(2026-06-14) — 그러나 10.3.1(2026-05-16)에 미포함. `main`에만 존재** | **치명.** 시연 기기가 Android 16 |
| [#4192](https://github.com/rnmapbox/maps/issues/4192) | 제스처 중 `onCameraChanged`가 네이티브 스로틀 없이 JS로 폭주 | open | 지도 이동에 맞춰 캐릭터 좌표를 갱신하면 이 경로를 탐. **JS 스레드 병목의 1순위 후보** |
| [#4206](https://github.com/rnmapbox/maps/issues/4206) | Android에서 `MarkerView`가 간헐적으로 사라짐 | open | 캐릭터 오버레이를 `MarkerView`로 구현하면 직격 |
| [#4213](https://github.com/rnmapbox/maps/issues/4213) | Android에서 `onCameraChanged`가 실행 직후 `[0,0]`으로 발화 | open | 초기 카메라 위치 기반 로직 주의 |
| [#4203](https://github.com/rnmapbox/maps/issues/4203) | 화면이 포커스되지 않은 상태에서 `centerCoordinate` 변경이 카메라를 안 움직임 | open | 탭 전환 시 지도 상태 (§6.1) |

**결정**

1. **10.3.1을 기본으로 시작**합니다
2. **스파이크 1번에서 `adb logcat`에 `ViewManager ... must override getDelegate` SoftException이 찍히는지 확인**합니다
3. 프리즈 또는 SoftException이 재현되면 → **`main` 커밋 [`591e248`](https://github.com/rnmapbox/maps/commit/591e248858afe6b5d90c1c80efd0060f8f223b5c) 고정** 또는 `patch-package`로 #4228을 백포트
4. 캐릭터 오버레이는 `MarkerView`(#4206)가 아니라 **지도 위 절대 위치 View + 좌표 변환**을 1안으로 검토합니다 (§7.1)
5. `onCameraChanged` 핸들러는 **처음부터 스로틀을 걸어** 작성합니다 (#4192, #4213)

> **v1.0 정정** — v1.0 §7은 "라이브러리 버전을 시작 시점에 고정하고 종료까지 변경하지 않는다"고 했습니다. **이 정책은 폐기합니다.** #4194 때문에 rnmapbox는 **릴리스를 주시해야 하는 유일한 예외**입니다. 나머지 라이브러리는 고정을 유지합니다.

### 5.3 토큰: Public Token 하나만

> **v1.0 정정 — Secret Token(`sk.`) 챕터 전체를 폐기합니다.**
>
> v1.0은 "Secret Token은 `DOWNLOADS:READ` 스코프가 필요하며 없으면 빌드 자체가 실패합니다"라고 썼습니다. **사실이 아닙니다.**
>
> [PR #4124](https://github.com/rnmapbox/maps/pull/4124) (2025-12-21 머지, **v10.3.0 포함**):
> > Download token (`RNMAPBOX_MAPS_DOWNLOAD_TOKEN`) is **no longer required by Mapbox** for accessing their SDKs.
>
> config plugin prop에도 `@deprecated Download token is no longer required by Mapbox. Do not set this.`가 달렸습니다. **우리가 쓰는 10.3.1이 바로 그 계열입니다.**

**필요한 것은 Public Token(`pk.`) 하나입니다.**

```bash
# frontend/.env.local  (gitignore 대상)
EXPO_PUBLIC_MAPBOX_TOKEN=pk.xxxxx
```

- `EXPO_PUBLIC_` 접두어가 붙은 값은 Expo가 앱 번들에 자동 포함합니다
- **앱에 포함되어 배포되므로 비밀이 아닙니다.** 유일한 보호 수단은 **Mapbox 대시보드의 URL/앱 제한**입니다
- `frontend/.env.example`에 키 이름만 적어 커밋합니다 (`infra/.env.example`과 동일 패턴)
- **GitLab CI/CD Variables 등록도, 팀원 간 비밀 전달 절차도 불필요합니다**

**즉시 실행**

1. 팀 공용 Mapbox 계정 생성 (개인 계정을 쓰면 그 팀원 부재 시 재발급이 막힘)
2. Public Token 확인 — 계정 생성 시 기본 발급
3. Mapbox 대시보드에서 해당 토큰에 앱 제한 설정
4. `frontend/.env.example` 작성 후 커밋

---

## 6. 앱 구조 결정

> v1.0에 **없던** 결정들입니다. §2.2의 도메인을 구현하려면 전부 필요합니다.

### 6.1 네비게이션: `expo-router`

**결정: `expo-router`** (파일 기반 라우팅)

| 항목 | `expo-router` | `@react-navigation/*` 직접 사용 |
|---|---|---|
| 기반 | react-navigation 위에 구축 | — |
| 라우트 정의 | `app/` 폴더 구조 = 라우트 | 코드로 Navigator 트리 구성 |
| 딥링크 | 파일 경로에서 자동 생성 | 수동 `linking` 설정 |
| 인증 게이팅 | 라우트 그룹 `(auth)` / `(app)` + redirect | 조건부 Navigator 분기 |
| Expo 통합 | 1급. SDK 57에서 계속 개선 중 | 별도 |

**근거**

- **화면이 20개 이상**입니다. Navigator 트리를 코드로 관리하면 파일 위치와 라우트 구조가 어긋나기 시작합니다
- **딥링크가 필요합니다.** 푸시 알림(§6.5)이 게시글·채팅방으로 이동해야 합니다. `notification` 테이블에 `notification_target`이 있습니다
- **인증 게이팅이 명확합니다.** `(auth)` / `(app)` 그룹으로 로그인 전후를 폴더로 분리합니다
- 내부가 react-navigation이므로 자료·API가 그대로 통합니다

**주의** — `expo-router`를 쓰면 **§9의 폴더 구조가 `app/` 규약에 종속**됩니다. 화면 파일은 `app/`, 재사용 컴포넌트·훅·API는 `src/`로 분리합니다.

**라우트 초안**

```
frontend/app/
├── (auth)/            로그인, 소셜 콜백
├── (app)/
│   ├── (tabs)/        지도 | 커뮤니티 | 스터디 | 채팅 | 마이
│   ├── apartment/[id]
│   ├── field/[sessionId]      임장 세션 + 체크리스트
│   ├── post/[id]
│   ├── study/[id]
│   ├── chat/[roomId]
│   └── chatbot
└── _layout.tsx        인증 상태에 따른 redirect
```

### 6.2 상태관리: TanStack Query + zustand

**결정: 서버 상태는 TanStack Query, 클라이언트 전역 상태는 zustand.**

> **v1.0 정정** — v1.0은 TanStack Query를 §12 본문에서 논증 전제로만 쓰고 결정 요약표·미결정 목록 어디에도 올리지 않았습니다. 결정으로 명시합니다.

**TanStack Query가 맡는 것** — 아파트 목록·상세, 게시글 무한스크롤, 스터디, 임장 기록, 챗봇 히스토리. 캐싱·로딩·재시도·무효화를 전담합니다.

**zustand가 맡는 것** — 인증 상태, 내 캐릭터 상태(위치·방향), 지도 카메라, 모달 등 UI 상태. 이것뿐입니다.

**Redux Toolkit을 쓰지 않는 이유** — 전역 상태가 위 4개뿐이라 구조 강제가 과잉이고, 학습 시간이 2~3주에서 구조적 장점보다 중요합니다. **단, 팀에 Redux 경험자가 여러 명이고 zustand 경험자가 없다면 익숙한 쪽이 낫습니다.**

**⚠️ 캐릭터 애니메이션 상태를 zustand에 넣지 마십시오.** 프레임 인덱스처럼 초당 수 회 이상 갱신되는 값을 전역 상태에 두면 리렌더가 발생합니다. **Reanimated shared value**로 UI 스레드에 둡니다. 전역 상태에는 "어느 방향으로 움직이는 중인가" 같은 **저빈도 상태**만 둡니다.

**같은 원칙이 지도에도 적용됩니다.** `onCameraChanged`(§5.2 #4192) 결과를 매번 zustand에 쓰면 지도 이동 내내 리렌더가 돕니다. 카메라 위치는 shared value 또는 ref로 두고, 화면 전환에 필요한 시점에만 store에 커밋합니다.

### 6.3 인증: `expo-secure-store` + 카카오 로그인

**토큰 저장: `expo-secure-store`**

Android Keystore 기반 암호화 저장소입니다. **`AsyncStorage`에 JWT를 넣지 않습니다** — 평문입니다.

- Access Token: 메모리 + SecureStore
- Refresh Token: SecureStore만
- 401 응답 시 TanStack Query의 전역 에러 핸들러에서 재발급 → 실패 시 `(auth)`로 redirect

**소셜 로그인: `@react-native-seoul/kakao-login` 5.x**

`social_account` 테이블이 요구하는 기능입니다.

| 필요 작업 | 위치 |
|---|---|
| config plugin 등록 (`kakaoAppKey`) | `app.config.ts` |
| Kakao SDK Maven 저장소 선언 | `expo-build-properties` plugin |
| **Android 키해시 등록** | 카카오 개발자 콘솔 |
| development build 재생성 | 네이티브 코드 추가이므로 필수 |

**⚠️ 키해시는 debug/release가 다릅니다.** 두 개 모두 콘솔에 등록해야 하며, EAS Build를 쓰면 EAS가 관리하는 서명 키의 해시도 필요합니다. **이것이 §13 스파이크 3번인 이유입니다.**

네이버 로그인이 요구사항에 포함되면 `@react-native-seoul/naver-login`을 동일 패턴으로 추가합니다.

### 6.4 위치: `expo-location`

> v1.0에 **`expo-location`이 0회** 등장했습니다. 지도·PostGIS·임장 도메인이 있는 앱에서 누락입니다.

| 항목 | 결정 |
|---|---|
| 라이브러리 | `expo-location` |
| 권한 | **포그라운드만** (`ACCESS_FINE_LOCATION`). 백그라운드 추적은 범위 밖 |
| 권한 문구 | `app.config.ts`의 config plugin에 한글로 명시 |
| 획득 방식 | `watchPositionAsync`, `accuracy: Balanced` |
| 갱신 주기 | **`distanceInterval` 기준** (초기값 10m). 시간 기준 폴링을 쓰지 않음 |
| 지도 연동 | rnmapbox `LocationPuck` |

**권한 거부 플로우를 처음부터 만듭니다.** 거부 → 기능 제한 안내 → "설정으로 이동"(`Linking.openSettings()`). 지도 화면이 첫 화면이므로 **권한 거부 시 앱이 빈 화면이 되지 않아야 합니다.**

**배터리** — 임장 세션(`field_session`) 중에만 `watchPositionAsync`를 켜고, 세션 종료·화면 이탈 시 반드시 해제합니다.

### 6.5 푸시: `expo-notifications` (FCM 직결)

**결정: `expo-notifications`.** `@react-native-firebase/messaging`을 쓰지 않습니다.

**근거**

- 백엔드에 `fcm_token` 테이블이 있습니다 → **서버가 FCM HTTP v1으로 직접 발송**합니다. Expo Push Service를 경유하지 않습니다
- `expo-notifications`의 **`getDevicePushTokenAsync()`가 네이티브 FCM 토큰을 그대로 반환**합니다. 그 값을 `fcm_token`에 저장하면 됩니다
- Firebase 네이티브 SDK를 추가하지 않아 빌드 구성이 단순합니다
- Android 단독 시연이라 APNs 고려가 없습니다

**필요 작업**

| 항목 | 내용 |
|---|---|
| `google-services.json` | Firebase 콘솔에서 발급. **`.gitignore`에 이미 등재돼 있음** → 팀 공유 경로 별도 결정 필요 (§14) |
| `app.config.ts` | `android.googleServicesFile` 지정 |
| 권한 | Android 13+ `POST_NOTIFICATIONS` 런타임 요청 |
| 딥링크 | 수신 payload → `expo-router` 경로 (§6.1) |

### 6.6 파일 업로드

| 단계 | 라이브러리 | 비고 |
|---|---|---|
| 선택 | `expo-image-picker` | 갤러리 + 카메라. 임장 리포트 증빙(`report_evidence`) |
| 압축·리사이즈 | `expo-image-manipulator` | **업로드 전 필수.** 원본 12MP를 그대로 올리지 않음 |
| 업로드 | `fetch` + FormData 또는 presigned URL | 백엔드 `file_meta` 설계에 따름 (§14) |
| 표시·캐시 | `expo-image` | `<Image>` 대신 사용. 캐시 정책 내장 |

**업로드 방식(직접 전송 vs presigned URL)은 백엔드와 합의가 필요합니다** → §14.

### 6.7 실시간: `@stomp/stompjs`

**결정: WebSocket + STOMP.** 백엔드가 Spring이므로 `spring-websocket` + STOMP가 기본 경로입니다.

| 항목 | 결정 |
|---|---|
| 클라이언트 | `@stomp/stompjs` (RN에서 네이티브 WebSocket 사용, SockJS 폴백 불필요) |
| 인증 | CONNECT 프레임 헤더에 Access Token |
| 재연결 | 라이브러리 자동 재연결 + 앱 포그라운드 복귀 시 재구독 |
| 구독 대상 | 채팅방(`chat_message`), 주변 사용자 위치 |
| 읽음 처리 | `chat_read_status` — 화면 진입/이탈 시 배치 전송 |

**⚠️ 위치 브로드캐스트 주기와 서버 응답 상한을 API 설계에 명시해야 합니다.** 클라이언트가 캐릭터 3개만 그리는데 서버가 100개를 내려주면 네트워크와 파싱이 낭비됩니다 → §14.

### 6.8 폼: `react-hook-form`

임장 체크리스트(`checklist_item`, `checklist_answer`)가 **동적 필드 배열**입니다. 스터디 신청·게시글 작성도 폼입니다. 비제어 방식이라 입력마다 리렌더가 없고, 체크리스트처럼 필드가 많은 화면에서 차이가 납니다.

---

## 7. 애니메이션

### 7.1 캐릭터: 스프라이트 시트

**Rive를 쓰지 않습니다.** Rive는 벡터 도형·메시·본을 State Machine으로 제어하는 도구이며 **프레임 단위 래스터 재생 프리미티브가 없습니다.** 벡터 리깅으로 바꾸면 아트 전면 재작업이고, 2D 벡터에서 8방향 회전은 결국 방향별 아트가 따로 필요해 이득이 없습니다.

#### 구현 방식

동시 표시가 **최대 3개**로 확정되어 절대 부하가 낮습니다.

| 방식 | 내용 | 판단 |
|---|---|---|
| **A. Image + Reanimated** | 클리핑 `View`(`overflow: hidden`) 안에 시트를 넣고 `translateX/Y`로 프레임 이동 | **확정.** 3개 규모에서 충분 |
| B. Skia Canvas | `@shopify/react-native-skia`로 `srcRect`만 변경 | 폴백. 확장성 우수, 학습 비용·신규 네이티브 의존성 발생 |

**A안으로 확정합니다** (2026-07-25, 클라이언트 결정 — §14 항목 2 해소).

**근거**

1. 동시 3개는 A안의 뷰 수 부담(캐릭터당 2뷰 = 총 6뷰)이 문제되는 규모가 아닙니다
2. Reanimated 4.5는 SDK 57 번들이므로 **신규 의존성이 0개**입니다. Skia는 rnmapbox·SDK 57·RN 0.86 조합에 검증되지 않은 네이티브 모듈을 하나 더 얹습니다 (§4.3의 검증 공백을 확대)
3. 개발 기간 2~3주에서 학습 비용을 지출할 자리가 아닙니다
4. §7.1의 "3개 확정으로 불필요해진 것"(클러스터링·컬링·LOD·아틀라스 배칭)이 곧 B안의 장점 목록입니다. **B안의 이점이 이 프로젝트에서는 대부분 무효입니다**

> **두 방식의 차이는 스레드가 아니라 렌더 경로입니다.** A안이 JS 스레드, B안이 UI 스레드라는 대비는 틀렸습니다. 어느 쪽이든 UI 스레드에서 돌며, 차이는 **Android 뷰 계층 합성**(A) vs **단일 GPU 캔버스**(B)입니다.

#### B안 전환 트리거

**아래 조건에서만 Skia로 전환합니다. 하나라도 충족하지 않으면 A안을 유지합니다.**

| # | 조건 | 확인 방법 |
|---|---|---|
| 1 | 스파이크 2번에서 프레임 드랍이 관측됨 | 지도 드래그 중 프레임 유지 실패 |
| 2 | **그 원인이 캐릭터 렌더 경로로 특정됨** | 캐릭터 레이어를 제거해도 드랍이 재현되면 → 렌더 경로가 원인이 **아님**. #4192(§5.2) 또는 좌표 갱신 로직 문제이므로 스로틀·좌표 변환을 고칠 것 |
| 3 | 스로틀·좌표 변환 최적화로 해소되지 않음 | 2번 격리 후 재측정 |
| 4 | 또는 동시 표시 캐릭터 수 요구가 **3개에서 상향**됨 | 기획 변경. 수십 개 규모면 A안의 뷰 수가 선형 증가 |

**전환 비용은 하루 이하로 유지합니다** — 프레임 인덱스 계산·방향 판정·좌표 변환 로직을 렌더 컴포넌트와 분리해 작성하십시오. 교체 대상이 "무엇을 그릴지"가 아니라 **"어떻게 그릴지" 한 겹**이어야 합니다.

**추가로 확인할 A안 고유 리스크** — Android에서 `overflow: hidden` 클리핑과 `transform`이 함께 걸릴 때의 엣지케이스(클리핑 누락, `elevation`/`zIndex` 간섭)입니다. 스파이크 2번에서 지도 위 실제 배치로 눈으로 확인하십시오.

**지도 위 배치는 `MarkerView`를 쓰지 않습니다.** [#4206](https://github.com/rnmapbox/maps/issues/4206)(Android에서 MarkerView 소실) 때문입니다. 대신 지도와 형제 레이어에 절대 위치 View를 두고, 좌표 → 화면 위치 변환을 **UI 스레드에서** 수행합니다.

**어느 방식이든 애니메이션은 반드시 Reanimated(UI 스레드)에서 실행하고 JS 리렌더로 프레임을 넘기지 않습니다.**

#### 에셋 스펙

**⚠️ fps보다 "동작 길이"를 먼저 확정하십시오.**

> v1.0은 "8~12fps로 충분"이라 했습니다. 37프레임 ÷ 10fps = **3.7초/사이클**입니다. 걷기 모션이면 슬로우모션입니다.
>
> **37프레임이 무슨 동작이고 몇 초짜리인지**를 디자인 담당에게 먼저 확인하고, `fps = 37 ÷ 목표초` 로 역산하십시오. 걷기 1.5초면 약 25fps입니다. 지금 순서가 반대였습니다.

**미러링** — 8방향 중 좌우 대칭을 활용해 N, NE, E, SE, S 5방향만 제작하고 SW/W/NW는 `scaleX: -1`로 처리합니다. **296 → 185프레임 (37% 절감).**

> 캐릭터에 비대칭 요소(한쪽 어깨 가방, 한 손에 든 물건)가 있으면 반전 시 드러납니다. **아트 확정 전 디자인 담당과 협의 필수.**

**아틀라스 분할** — **방향당 1장**, 각 시트에 37프레임을 그리드 배치.

**프레임 크기 상한** — 아래는 **동시 상주 기준**입니다.

| 프레임 크기 | 아틀라스 1장(37프레임) | 캐릭터 3개 × 서로 다른 방향 | 판정 |
|---|---|---|---|
| 128×128 | 약 2.4MB | 약 7MB | **권장** |
| 192×192 | 약 5.5MB | 약 16MB | 권장 |
| 256×256 | 약 9.7MB | 약 29MB | 상한 |
| 512×512 | 약 39MB | 약 116MB | 불가 |

> **v1.0 정정** — v1.0은 "185프레임 총량"으로 계산해 128px에 12MB, 512px에 194MB를 제시했습니다. 그러나 같은 절에서 **"방향당 1장 분할"** 을 권장하므로 전량이 동시에 디코딩되지 않습니다. 전제와 결론이 모순이었습니다. 위 표는 분할 전제로 재계산했습니다.

화면 표시 크기(3x 밀도에서 논리 40~64dp)를 고려하면 **128~192px로 충분**합니다.

**포맷** — PNG 대신 **WebP**. Android 네이티브 지원이며 무손실 기준 PNG보다 25~35% 작습니다.

**재생 fps 분리** — S23은 120Hz입니다. 렌더 프레임마다 스프라이트를 넘기면 안 됩니다. 경과 시간 기준으로 계산합니다.

```
frameIndex = floor(elapsedMs / (1000 / spriteFps)) % 37
```

#### 3개 확정으로 불필요해진 것

클러스터링, 뷰포트 컬링, 거리별 LOD, 아틀라스 배칭, 단일 캔버스 일괄 드로우.

### 7.2 UI 요소: `lottie-react-native`

**Rive를 도입하지 않습니다.**

| 항목 | `lottie-react-native` | `rive-react-native` |
|---|---|---|
| 제공 주체 | **Airbnb 공식 네이티브 라이브러리 래퍼** | Rive 공식 |
| 디자이너 워크플로 | **After Effects + Bodymovin (업계 표준)** | Rive 에디터 신규 학습 |
| 렌더 정확도 | 네이티브 렌더러라 AE 원본과 동일 | Rive 자체 포맷 |
| 생태계 | 성숙, 한국어 자료 풍부 | 상대적으로 작음 |

**근거** — (1) 에셋 제작 도구가 둘로 나뉘면 디자이너가 두 도구를 오가고 개발자가 두 API를 배웁니다. 2~3주 일정에 부담입니다. (2) 버튼 눌림·토글·로딩·성공 체크 수준은 Lottie의 구간 재생(`play(start, end)`), 속도·방향 제어(`speed`, 음수로 역재생), 진행도 직접 제어(`progress` prop을 Reanimated 값과 연결)로 커버됩니다.

**예외** — 복잡한 다단계 상태 전이나 드래그 연동 애니메이션이 필요한 요소가 나오면, **그 요소에 한해** Rive를 재검토합니다. 초기부터 두 라이브러리를 넣지 않습니다.

---

## 8. 네이티브 레이어

### 8.1 Kotlin 유지

`MainActivity`, `MainApplication`은 **템플릿 기본값인 Kotlin을 유지**합니다.

**근거는 "위험"이 아니라 "이득 없는 추가 작업"입니다.**

1. **라이브러리 설치 가이드가 전부 Kotlin 기준** — `@rnmapbox/maps`, `lottie-react-native`, 카카오 로그인, FCM. Java 유지 시 매번 수동 번역
2. **업그레이드 경로** — RN Upgrade Helper diff가 Kotlin으로 제공
3. **성능·빌드 이득 없음** — [배경 문서 §3](./ANDROID_BACKGROUND.md)에서 검증

앱 코드의 대부분은 TypeScript이므로 Kotlin 파일을 직접 편집할 일은 드뭅니다.

> **v1.0 정정 유지** — v1.0이 철회한 "Java 변환 시 구 아키텍처 회귀" 주장은 여전히 무효입니다. [RN 0.82부터 New Architecture 비활성화 옵션이 제거](https://github.com/facebook/react-native/pull/53025)되어 `newArchEnabled=false`는 무시됩니다. RN 0.86에서는 더더욱 해당 없습니다.

### 8.2 직접 손대는 네이티브 파일 (재산정)

> **v1.0 정정** — v1.0은 "직접 작성할 네이티브 파일 0~1개"라고 했습니다. **소셜 로그인과 FCM을 빠뜨린 결과입니다.**

| 목적 | 처리 | 네이티브 파일 직접 편집 |
|---|---|---|
| 앱 이름·아이콘·스플래시 | `app.config.ts` | ❌ |
| 권한 (위치, 알림) | `app.config.ts` | ❌ |
| Mapbox | config plugin | ❌ |
| Lottie | config plugin | ❌ |
| 흔들기 센서 | `expo-sensors` (권한 불필요) | ❌ |
| **카카오 로그인** | config plugin + `expo-build-properties`(Maven) + **키해시 콘솔 등록** | ❌ (설정은 많음) |
| **FCM** | `google-services.json` + `app.config.ts` | ❌ |
| HTTP 허용 (개발용) | `expo-build-properties` | ❌ |
| **흔들기 Dev Menu 비활성화** | §8.4 | **△ 유일한 후보** |

**결론: 직접 편집할 네이티브 소스는 여전히 0~1개이나, `app.config.ts`의 네이티브 설정량은 v1.0 추정의 3배 이상입니다.** "네이티브를 거의 안 만진다"는 말과 "설정이 간단하다"는 말은 다릅니다.

### 8.3 `frontend/android`는 커밋하지 않습니다

> **v1.0 정정 — 결정을 뒤집습니다.** v1.0 §8.3(2)는 `android/` 커밋을 권장했습니다.

**Expo SDK 57에서 `expo prebuild`의 기본 동작이 바뀌었습니다.**

> `expo prebuild`: now **clears and regenerates** the native `android` and `ios` directories **by default**; pass `--no-clean` to apply changes to the existing folders instead. ([expo#47209](https://github.com/expo/expo/pull/47209))

즉 커밋된 `android/`에 수동 수정을 넣어두면 **누군가 `expo prebuild`를 돌리는 순간 조용히 날아갑니다.** v1.0의 "수동 수정분을 안전하게 보관"이라는 근거가 정확히 반대로 뒤집혔습니다.

**결정**

- `frontend/android`, `frontend/ios`를 **`.gitignore`에 추가**
- 네이티브 설정은 **전부 `app.config.ts`와 config plugin**으로 표현 (CNG, Continuous Native Generation)
- 수동 수정이 불가피하면 **custom config plugin을 작성**해 `plugins/`에 커밋. 생성물이 아니라 생성 규칙을 버전 관리합니다
- 빌드 문제 추적 시에는 로컬에서 `expo prebuild` 후 생성 결과를 확인 (커밋하지 않음)

### 8.4 흔들기 Dev Menu 충돌

debug 빌드에서 기기를 흔들면 개발자 메뉴가 열립니다. 흔들기가 핵심 기능이라 개발 내내 간섭합니다.

> **v1.0 정정** — v1.0은 "RN `DevSettings` API로 비활성화"를 1순위로 제시했으나, **RN의 JS `DevSettings` 모듈에 shake 토글은 없습니다.**

**대응 순서**

1. **`expo-dev-client`의 dev menu 모션 제스처 설정** — development build를 쓰므로 RN 기본 dev menu가 아니라 expo-dev-menu가 뜹니다. 여기에 제스처 설정이 있습니다. **1순위로 확인**
2. **대체 호출 사용** — `adb shell input keyevent 82` 또는 Metro 터미널의 `d` 키. shake 트리거만 끄면 개발에 지장 없음
3. **위가 불가하면 custom config plugin** — `MainActivity.kt`를 직접 수정하지 **않습니다** (§8.3에 따라 날아감)

**스파이크 4번에서 확인합니다.**

---

## 9. 프로젝트 구조

**앱 프로젝트 루트를 `frontend/`로 만듭니다.**

기존 `android/` 폴더 안에 생성하면 Expo가 그 안에 다시 `android/`를 만들어 `S15P11A701/android/android/app/build.gradle`이 됩니다.

```
S15P11A701/
├── frontend/                  ← 앱 프로젝트 루트
│   ├── app.config.ts
│   ├── package.json
│   ├── app/                   expo-router 라우트 (§6.1)
│   ├── src/
│   │   ├── components/
│   │   ├── features/          도메인별 (map, field, post, chat, study, chatbot)
│   │   ├── api/               TanStack Query 훅
│   │   ├── store/             zustand
│   │   └── lib/
│   ├── assets/
│   │   ├── sprites/           방향별 아틀라스 (§7.1)
│   │   └── lottie/
│   ├── plugins/               custom config plugin (§8.3)
│   ├── android/               ← 생성물. 커밋하지 않음
│   └── ios/                   ← 생성물. 커밋하지 않음
├── backend/
├── ai/
├── infra/
└── docs/
```

**착수 시 작업**

1. 기존 `android/.gitkeep` 제거
2. `docs/LOCAL_DEVELOPMENT.md`의 "프로젝트 구조" 항목을 `android` → `frontend`로 수정
3. `.gitignore`에 아래 추가

```gitignore
# React Native / Node
node_modules/
npm-debug.log
*.hprof
.expo/

# Expo prebuild 생성물 (§8.3)
frontend/android/
frontend/ios/
```

이미 커버됨: `**/build/`, `local.properties`, `.idea/`, `*.keystore`, `*.jks`, `google-services.json`, `.env`, `.env.*`, `!.env.example`

> `backend/`가 있으므로 `frontend/`가 대칭적입니다. 웹 프론트엔드를 추가할 계획이 생기면 `mobile/`로 바꿉니다.

---

## 10. 빌드·배포

> v1.0에 없던 항목입니다. 시연 산출물까지 경로를 정의합니다.

| 단계 | 방법 |
|---|---|
| 로컬 개발 | `npx expo run:android` (development build) |
| 팀 배포용 debug APK | EAS Build `development` 프로파일 또는 로컬 |
| **시연용 release APK** | EAS Build `preview` 프로파일 (내부 배포용 APK) |
| 서명 키 | **EAS가 관리하는 키 사용.** 로컬 keystore를 만들면 카카오 키해시를 다시 등록해야 함 (§6.3) |
| EAS 무료 한도 | **Android 15회/월.** 매 커밋 빌드 금지, 필요 시점에만 |

**⚠️ release 빌드를 마지막 날 처음 돌리지 마십시오.** debug에서 되던 것이 release에서 깨지는 대표 원인은 ProGuard/R8 난독화와 `__DEV__` 분기입니다. **1주차 종료 시점에 release APK를 한 번 만들어 S23에 설치해 봅니다.**

**S23 사전 준비** — 소프트웨어 버전 확인(설정 → 휴대전화 정보), 자동 업데이트 차단, 개발자 옵션 활성화, 배터리 최적화 예외 등록.

---

## 11. IDE와 팀 규약

**에디터는 개인 선택입니다.** v1.0은 IntelliJ Ultimate 단일화를 권장했으나, 이는 결정이 아니라 취향이며 라이선스 보유 여부에 따라 달라집니다. 배경 설명은 [배경 문서 §4](./ANDROID_BACKGROUND.md).

**저장소 설정으로 강제하는 것**

| 항목 | 내용 |
|---|---|
| `.editorconfig` | 들여쓰기, 줄바꿈 (이미 존재) |
| Prettier | `endOfLine: "lf"` — Windows 팀원 CRLF 혼입 방지 |
| ESLint | `eslint-config-expo` |
| TypeScript | `strict: true` |
| lockfile | `package-lock.json` 커밋 |

**Android SDK는 별도 확보가 필요합니다.** Android Studio를 한 번 설치해 SDK/NDK를 내려받는 것이 가장 확실합니다. 이후 Android Studio는 AVD·Logcat·Gradle 문제 추적 시에만 씁니다.

---

## 12. 리스크 레지스터

| # | 리스크 | 확률 | 영향 | 대응 | 검증 |
|---|---|---|---|---|---|
| R1 | **Mapbox 한국 라벨·POI 품질 미달** | 중 | **치명 — 제품 불성립** | 제약 제공 주체에 에스컬레이션 | 스파이크 0 |
| R2 | **rnmapbox #4194 Android 16 프리즈** | 중 | **치명 — 시연 기기** | `main` 커밋 고정 또는 patch-package | 스파이크 1 |
| R3 | rnmapbox × SDK 57 미검증 조합 | 중 | 높음 | SDK 56으로 하향 (§4.4) | 스파이크 1 |
| R4 | 흔들기 ↔ Dev Menu 충돌 | 높음 | 중 | expo-dev-menu 설정 → adb 대체 호출 | 스파이크 4 |
| R5 | 카카오 키해시 debug/release 불일치 | 높음 | 중 | 두 해시 모두 사전 등록, EAS 키 사용 | 스파이크 3 |
| R6 | `onCameraChanged` JS 폭주 (#4192) | 중 | 중 | 핸들러에 처음부터 스로틀. **Skia 전환은 이 리스크의 대응이 아님** (§7.1) | 스파이크 2-b |
| R6′ | A안 스프라이트 렌더 부하 / Android 클리핑 엣지케이스 | 낮 | 중 | B안(Skia) 전환 — 트리거는 §7.1 | 스파이크 2-a |
| R7 | 아트 재작업 (비대칭·프레임 크기·fps) | 중 | 높음 | 발주 전 스펙 확정 (§7.1) | 아트 착수 전 |
| R8 | release 빌드 실패 (R8/`__DEV__`) | 중 | 높음 | 1주차에 release APK 1회 생성 | 1주차 말 |
| R9 | EAS 무료 15회 소진 | 낮 | 중 | 로컬 빌드 병행, 빌드 시점 통제 | 상시 |

---

## 13. 1~2일차 스파이크

**이 스파이크 한 번으로 본 문서의 추정치 대부분이 실측으로 대체됩니다.** 순서가 중요합니다 — 앞 항목이 실패하면 뒤 항목이 무의미해집니다.

### 1일차 오전 — 착수

1. `frontend/` 생성, **Expo SDK 57 고정** (`npx create-expo-app`)
2. Mapbox 팀 공용 계정 + Public Token 발급 (§5.3)
3. `.env.example` 작성, `.gitignore` 갱신 (§9)

### 1일차 — 검증 (순서 고정)

| # | 항목 | 판정 기준 | 실패 시 |
|---|---|---|---|
| **0** | **S23에서 대상 아파트 단지를 Mapbox로 렌더** | **한글 라벨·단지명·주변 POI가 시연 가능한 수준인가** | **에스컬레이션 (R1). 여기서 막히면 아래를 진행하지 않음** |
| **1** | 지도 마운트 + `adb logcat` 확인 | `ViewManager ... must override getDelegate` SoftException이 **없어야** 함. 앱이 프리즈하지 않아야 함 | rnmapbox `main` 커밋 고정 → 그래도 실패 시 SDK 56 하향 (R2, R3) |
| **2-a** | 지도 위 스프라이트 3개 (A안) **정지 상태** | 프레임 유지. `overflow: hidden` 클리핑이 지도 위에서 정상 동작 | A안 렌더 경로 문제 → B안 전환 트리거 확인 (§7.1, R6′) |
| **2-b** | 위 상태에서 **카메라 이동** | 드래그 중 프레임 유지, `onCameraChanged` 스로틀 동작 | **스로틀·좌표 변환을 먼저 고칠 것** (R6). 2-a가 통과했다면 원인은 캐릭터 렌더가 아니므로 **B안으로 전환하지 말 것** |
| 3 | 카카오 로그인 1회 성공 | debug 키해시로 토큰 수신 | 키해시 재등록 (R5) |
| 4 | 흔들기 감지 + Dev Menu 억제 | 흔들어도 dev menu가 안 뜨고 이벤트만 발생 | adb 대체 호출로 우회 (R4) |
| 5 | 실기기에서 백엔드 연결 | `http://<LAN IP>:8080/actuator/health` 200 | `expo-build-properties`로 cleartext 허용 |
| 6 | 실제 Lottie `.json` 렌더 | AE 원본과 동일 | 에셋 재출력 |

### 2일차

7. 스파이크 결과로 §14 항목 1·2 확정
8. 아트 스펙 확정 후 **제작 착수** (§7.1)
9. `expo-router` 라우트 골격 + 인증 게이팅 구현
10. 본 개발 착수

---

## 14. 남은 결정

**성격이 셋으로 갈립니다. 같은 "미결정"이 아닙니다.**

| 성격 | 해당 | 뜻 |
|---|---|---|
| **인간이 결정해야 함** | 항목 1 | 물어봐야 답이 나옵니다. **착수 전 유일한 블로커** |
| **기능 착수 시점에 결정** | 항목 3~8 | 해당 기능을 만들 때 정하면 됩니다. 지금 막지 않습니다 |

> **v2.1** — 구v2.0의 항목 2(스프라이트 구현 방식)는 **A안으로 확정**되어 이 표에서 내려갔습니다. 스파이크 2번은 결정 수단이 아니라 **A안의 검증**으로 성격이 바뀌었습니다 (§7.1).

| # | 항목 | 결정 주체 | 기한 | 우선순위 |
|---|---|---|---|---|
| 1 | **캐릭터 에셋 스펙** — 37프레임의 **동작 길이(초)**, 비대칭 요소 유무, 프레임 크기(≤256px), 미러링 적용, WebP 변환 | 디자인 + 클라이언트 | **아트 착수 전** | **높음** |
| 3 | **`google-services.json` 공유 방법** — `.gitignore` 대상이므로 팀 배포 경로 필요 | 팀 | 1주차 | **높음** |
| 4 | **파일 업로드 방식** — 서버 직접 전송 vs presigned URL, 이미지 최대 해상도·용량 | 백엔드 + 클라이언트 | 1주차 | **높음** |
| 5 | 실시간 API 계약 — 위치 브로드캐스트 주기, **서버 응답 사용자 수 상한**, 채팅 구독 경로 | 백엔드 + 클라이언트 | 1주차 | 중간 |
| 6 | 네이버 로그인 포함 여부 | 기획 | 1주차 | 중간 |
| 7 | 디자인 시스템 — 색상·타이포·컴포넌트 규약 | 디자인 | 1주차 | 중간 |
| 8 | 에러 모니터링 도입 여부 (Sentry 등) | 팀 | 2주차 | 낮음 |

**확정 완료** — 프레임워크(§3), Expo SDK·RN 버전(§4), 지도 라이브러리(§5), 네비게이션·상태관리·인증·위치·푸시·업로드·실시간·폼(§6), 애니메이션 도구·**스프라이트 구현 방식**(§7), 네이티브 언어·`android/` 커밋 정책(§8), 폴더 구조(§9), 빌드 경로(§10), 팀 규약(§11), 패키지 매니저·동시 캐릭터 수(§1)

---

## 15. 변경 이력

### v2.1 (2026-07-25) — 스프라이트 구현 방식 확정

**확정된 결정**

| 항목 | v2.0 | v2.1 | 사유 |
|---|---|---|---|
| 스프라이트 구현 방식 | A안 1안, 스파이크 2 결과로 확정 | **A안 확정** (Skia는 조건부 폴백) | 신규 네이티브 의존성 0개, 3개 규모에서 B안의 이점이 무효 (§7.1) |

**정정된 내용**

- **스파이크 2번의 판정 기준이 두 원인을 섞고 있었습니다.** "프레임 유지"와 "`onCameraChanged` 스로틀 동작"을 한 항목에 넣고, 실패 시 대응을 일괄 "B안 전환 검토 (R6)"으로 걸어두었습니다. R6는 지도 이벤트가 JS 스레드를 때리는 문제이므로 **Skia로 바꿔도 해소되지 않습니다.** 항목을 **2-a(정지 상태 = 렌더 경로)** 와 **2-b(카메라 이동 = 좌표 갱신 경로)** 로 분리하고, 각각의 대응을 갈랐습니다 (§13)
- 리스크 레지스터에서 R6를 R6(이벤트 폭주)와 **R6′(A안 렌더 부하·Android 클리핑)** 으로 분리 (§12)

**추가된 내용**

- §7.1에 **B안 전환 트리거 4개 조건** 명시. 전환 비용을 하루 이하로 유지하기 위한 코드 분리 지침(프레임 계산 ↔ 렌더 컴포넌트) 포함
- A안 고유 리스크로 Android `overflow: hidden` + `transform` 엣지케이스 기록
- "A안=JS 스레드 / B안=UI 스레드" 오해에 대한 경고 — 실제 차이는 뷰 계층 합성 vs 단일 GPU 캔버스

**미결정 잔존** — 8건 → **7건** (높음 3건 / 중간 3건 / 낮음 1건). 착수 전 인간 판단 필요 항목은 여전히 1건(§14 항목 1, 캐릭터 에셋 스펙)입니다.

---

### v2.0 (2026-07-25) — v1.0 리뷰 반영

**뒤집힌 결정**

| 항목 | v1.0 | v2.0 | 사유 |
|---|---|---|---|
| RN 버전 | 0.84.1 확정 | **Expo SDK 57 / RN 0.86** | RN 0.84를 싣는 안정 Expo SDK가 없음. 두 결정이 양립 불가였음 (§4.1) |
| `android/` 커밋 | 커밋 권장 | **커밋하지 않음** | SDK 57에서 `expo prebuild`가 기본으로 네이티브 폴더를 삭제·재생성 (§8.3) |
| 라이브러리 버전 고정 | 전면 고정 | **rnmapbox만 예외** | #4194 수정이 미released (§5.2) |
| IDE | IntelliJ Ultimate 단일화 권장 | **개인 선택** | 결정이 아니라 취향 (§11) |

**폐기된 내용**

- **§11 Mapbox Secret Token 챕터 전체** — `RNMAPBOX_MAPS_DOWNLOAD_TOKEN`은 [PR #4124](https://github.com/rnmapbox/maps/pull/4124)(v10.3.0 포함)에서 폐기. "없으면 빌드 실패"는 사실이 아니었음. 즉시 실행 항목 3건과 GitLab CI Variables 등록이 함께 소멸
- **§7 0.84 vs 0.85 비교표** — Node 최소 버전이 양쪽 다 틀렸고(실제 0.84.1은 `>=20.19.4`), Metro·jest-preset 차이를 누락했으며, "Android 빌드 설정"이라는 제목에 Node·TypeScript·React를 넣어 범주가 혼재했음
- **"RN `DevSettings` API로 shake 비활성화"** — 해당 API 없음 (§8.4)
- **"직접 작성할 네이티브 파일 0~1개"** — 소셜 로그인·FCM 누락 상태의 산정이었음 (§8.2)

**수정된 계산**

- 스프라이트 메모리 상한 — "185프레임 전량 상주" 전제가 같은 절의 "방향당 1장 분할" 권장과 모순. 분할 전제로 재계산 (§7.1)
- 재생 fps — 프레임 수가 아니라 **동작 길이**에서 역산하도록 순서 교정 (§7.1)

**추가된 결정 (v1.0에 없었음)**

네비게이션(§6.1), 인증·소셜 로그인(§6.3), 위치(§6.4), 푸시(§6.5), 파일 업로드(§6.6), 실시간(§6.7), 폼(§6.8), 빌드·배포(§10), 리스크 레지스터(§12)

**추가된 리스크**

- rnmapbox [#4194](https://github.com/rnmapbox/maps/issues/4194) Android 16 프리즈 — 시연 기기 직결, 수정 미released (§5.2)
- [#4192](https://github.com/rnmapbox/maps/issues/4192) `onCameraChanged` JS 폭주, [#4206](https://github.com/rnmapbox/maps/issues/4206) MarkerView 소실 (§5.2)
- Mapbox 한국 라벨·POI 품질 — 스택 결정으로 완화 불가, 스파이크 0번으로 승격 (§5.1)

**구조 변경**

- 설명성 내용(Flutter 렌더링 합성 모델, Java/Kotlin 성능·빌드 검증, 네이티브 모듈 종류, IDE 배경)을 [ANDROID_BACKGROUND.md](./ANDROID_BACKGROUND.md)로 분리
- "초판 정정" 블록을 본문에서 변경 이력으로 이관 (일부는 문맥상 인용 유지)
- 결정 요약표에 Expo SDK, 네비게이션, 상태관리, TanStack Query, 패키지 매니저 추가

### v1.0 (2026-07-25)

최초 작성. 프레임워크·RN 버전·Expo·Lottie·동시 캐릭터 수 결정.

---

## 재검토 트리거

아래가 발생하면 이 문서를 다시 엽니다.

- 스파이크 0번에서 Mapbox 라벨 품질이 시연 불가 판정 → §5 전면 재작성
- 스파이크 1번에서 rnmapbox × SDK 57 조합 실패 → §4를 SDK 56으로 수정
- `@rnmapbox/maps` v10.3.2 이상 릴리스 → §5.2의 `main` 커밋 고정 해제 여부 판단
- 요구사항에 백그라운드 위치 추적 추가 → §6.4 재설계 (포그라운드 서비스, 권한 2단계)
- 동시 표시 캐릭터 수 상향 → §7.1의 "불필요해진 것" 목록 전면 재검토
