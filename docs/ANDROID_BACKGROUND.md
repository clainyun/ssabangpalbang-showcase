# Android 스택 배경 설명

| | |
|---|---|
| 작성일 | 2026-07-25 |
| 성격 | **참고 자료.** 결정 문서가 아닙니다 |
| 결정 문서 | [ANDROID_STACK_DECISION.md](./ANDROID_STACK_DECISION.md) |

> 이 문서는 결정의 **배경 지식과 검증 과정**을 담습니다. 무엇을 쓸지는 결정 문서에 있습니다. 여기는 "왜 그렇게 판단했는가"와 "어떤 것을 확인해봤는가"입니다.
>
> **읽지 않아도 개발에 지장이 없습니다.** 결정에 이의가 있거나 나중에 같은 논의가 반복될 때 참고하십시오.

---

## 1. Flutter를 선택하지 않은 배경

결정 문서 §3에서 요약한 내용의 상세입니다.

### 1.1 원인은 Dart가 아니라 렌더링 합성 모델입니다

흔한 오해와 달리 Dart의 실행 속도 문제가 아닙니다. **Flutter가 네이티브 뷰를 자기 화면에 끼워 넣는 방식(PlatformView)의 비용**입니다.

| 스택 | 지도와 캐릭터의 관계 |
|---|---|
| React Native | 지도(MapView)도 캐릭터도 **같은 Android View 계층의 형제**. 겹쳐 그리는 데 추가 비용 없음 |
| Flutter | Flutter는 자체 캔버스에 그리고 Android View를 쓰지 않음. Mapbox는 네이티브 뷰이므로 **PlatformView로 끼워 넣어야 함**. 그 위에 Flutter 위젯(캐릭터)을 올리면 매 프레임 합성 단계가 추가됨 |

### 1.2 근거

- **Flutter 공식 문서**는 PlatformView 위에서 애니메이션이 느릴 경우의 회피책으로 *"네이티브 뷰의 스크린샷을 찍어 텍스처로 렌더링하라"* 고 안내합니다. PlatformView 위 애니메이션이 문제가 될 수 있음을 공식적으로 인정하는 내용이며, 제시된 해법(정지 이미지 대체)은 **지도가 계속 움직이는 우리 요구사항과 양립하지 않습니다**

- [**flutter/flutter#167547**](https://github.com/flutter/flutter/issues/167547) — "Hybrid composition can be slow on android". 2025-04-22 등록, 현재도 open (P2). 발생 기기 모델이 예측 불가능하며 **OnePlus 13 / SDK 35** 같은 신형 상급기에서도 나타난다고 보고됨

- Virtual Display 방식은 네이티브 뷰 픽셀이 중간 그래픽 버퍼를 추가로 거쳐 그래픽 메모리와 드로잉 성능을 소모하며, 고빈도 갱신 시 잰크를 유발

- 커뮤니티 정리에 따르면 PlatformView 오버헤드는 **"애니메이션이 없고 스크롤하지 않는 콘텐츠에 대해서는 수용 가능"** 수준. 우리 요구사항은 정확히 그 반대

### 1.3 이 근거의 한계 — 공정하게

**이 인용들은 결정을 뒤집을 만큼 강하지 않습니다.**

- **시효.** #167547의 보고 환경은 **Flutter 3.24.5 / 3.27, 2025년 4월**입니다. 2026년 현재 Flutter는 여러 버전 앞서 있고, 이 이슈가 그 버전들에서 어떤지는 재확인되지 않았습니다
- **HCPP.** Android API 34+ 및 Vulkan 지원 기기에서는 **Hybrid Composition++** 가 오버헤드를 크게 줄입니다. Galaxy S23(Android 16 = API 36, Adreno 740)은 조건을 만족하므로 실제 체감은 위 보고보다 나을 수 있습니다
- **규모.** 동시 캐릭터가 최대 3개로 확정되어 절대적 부하가 낮습니다

**따라서 정직한 서술은 이렇습니다.**

> "Flutter는 이 구조에서 느리다"가 아니라 **"Flutter에는 우리가 통제할 수 없는 변수가 하나 더 있고, 2~3주 일정에서 그것이 발현되면 회피할 시간이 없다"** 입니다. React Native에는 이 변수 자체가 존재하지 않습니다.

**실제 결정을 지배한 것은 팀의 React 경험과 일정입니다.** 위 렌더링 논의는 "RN을 골라도 지도 구조에서 손해 보지 않는다"를 확인하는 역할이었습니다.

### 1.4 부차적 비교

| 항목 | React Native | Flutter |
|---|---|---|
| Lottie 구현체 | `lottie-react-native` — **Airbnb 공식 네이티브 라이브러리(`lottie-android`) 래퍼** | `lottie` — 순수 Dart 재구현 **비공식 포트**. 일부 이펙트 미지원 가능성 |
| 학습 비용 | 팀의 React 경험을 재사용 | Dart 문법은 쉬우나 위젯 시스템 학습에 시간 소요 |
| 자료 접근성 | Stack Overflow 절대량과 한국어 자료가 많음 | 상대적으로 적음 |

### 1.5 "스프라이트는 Flutter가 유리"는 과장이었습니다

초기 검토에서 나온 평가이나 근거가 약합니다.

- `@shopify/react-native-skia`가 **스프라이트 시트 패턴을 공식 문서화**하고 있습니다
- Reanimated shared value와 결합하면 **UI 스레드에서 실행**되어 JS 스레드와 분리됩니다
- 엔진은 Flutter가 사용하던 것과 동일한 Skia입니다

게다가 동시 3개 규모면 Skia조차 필요 없이 `<Image>` + `overflow: hidden` + Reanimated `translateX/Y`로 충분합니다 (결정 문서 §7.1 A안).

---

## 2. 네이티브 Android를 선택하지 않은 배경

요구사항만 보면 매력적인 선택지였습니다.

- Mapbox **공식** Android SDK 제공
- 흔들기를 `SensorManager`로 직접 구현, Dev Menu 충돌 없음
- Android Studio 단일 IDE
- 시연 기기가 Android 단독이라 iOS 대응 불필요
- 백엔드가 Spring이라 팀의 Java 숙련도 존재

배제 사유는 **UI 생산성과 일정**입니다.

### 2.1 애니메이션·화면 요구량이 결정적이었습니다

캐릭터 스프라이트, UI 요소별 상호작용 애니메이션, 그리고 20개 이상의 화면. **UI 작업량이 많은 프로젝트**입니다.

Jetpack Compose는 **Kotlin 전용**입니다. Java를 유지하면 XML 레이아웃 + View 시스템(명령형)으로 작업해야 합니다.

리스트 화면 하나에 필요한 것:

1. `item_row.xml` 레이아웃
2. `ViewHolder` 클래스
3. `RecyclerView.Adapter` 상속 (`onCreateViewHolder`, `onBindViewHolder`, `getItemCount`)
4. 효율적 갱신을 위한 `DiffUtil.Callback`
5. Activity/Fragment에서 어댑터 연결 및 LayoutManager 설정

파일 3~4개, 100줄 이상입니다. 선언형 프레임워크에서는 10~20줄입니다. 화면마다 생명주기 처리, 화면 회전 시 상태 복원(`onSaveInstanceState`), ViewBinding 설정이 추가됩니다.

게시판·스터디·임장·채팅·챗봇을 합치면 리스트 화면만 10개 이상입니다.

### 2.2 Kotlin + Compose는 왜 아닌가

Compose를 쓰면 위 문제는 해소되나:

- **Java 사용이라는 원래 선호가 어차피 충족되지 않습니다**
- Android SDK 자체(생명주기, 권한, 백스택, Coroutine)의 학습이 필요합니다
- 팀에 Android 네이티브 경험자가 충분하지 않으면 2~3주에 위험합니다

**Java를 포기하는 순간 네이티브를 고를 유인이 크게 줄어들며**, 그렇다면 팀 경험을 재사용할 수 있는 React Native가 합리적입니다.

### 2.3 용어 정리 — 네이티브 Android는 프레임워크가 아닙니다

| 구분 | 정체 |
|---|---|
| Android | 운영체제(플랫폼) |
| Android SDK | 플랫폼 위에서 앱을 만드는 개발 키트 |
| Jetpack | Google 제공 라이브러리 모음 (Compose, Room, ViewModel 등) |
| React Native / Flutter | 프레임워크 |

"네이티브 안드로이드 개발"은 프레임워크 이름이 아니라 **크로스플랫폼 도구 없이 플랫폼 API를 직접 사용하는 방식**을 가리킵니다.

---

## 3. Java / Kotlin 성능·빌드 시간 검증 결과

결정 문서 §8.1이 "성능·빌드 이득이 없다"고 단정한 근거입니다. **결론: 차이 없음.**

### 3.1 런타임 성능 — 차이 없음

Java와 Kotlin은 동일한 JVM 바이트코드를 거쳐 DEX로 컴파일되며 R8 최적화도 동일합니다. 변환 대상 파일(`MainActivity`, `MainApplication`)은 **앱 시작 시 한 번만 실행되는 부트스트랩 코드**라 성능 격차가 발생할 구조가 아닙니다.

| 실제 성능 요인 | 언어 선택과의 관계 |
|---|---|
| New Architecture (Fabric / TurboModules) | 무관 (0.82+ 강제 활성화) |
| Hermes V1 (RN 0.84+ 기본) | 무관 |
| 애니메이션의 UI 스레드 실행 여부 | 무관 |
| 리스트 가상화 | 무관 |
| 이미지 캐싱 및 디코딩 | 무관 |

### 3.2 빌드 시간 — 차이 없음

Gradle은 앱 모듈 소스를 두 태스크로 나눠 컴파일합니다.

| 태스크 | 대상 |
|---|---|
| `compileDebugKotlin` | 모듈의 `.kt` 파일 |
| `compileDebugJavaWithJavac` | 모듈의 `.java` 파일 |

Kotlin과 Java는 상호 참조가 가능해 순환 의존이 생기므로, `kotlinc`가 먼저 실행되어 Java 소스를 **읽어서 심볼 테이블만 구성**하고 `.class`는 Kotlin 것만 생성합니다. 이후 `javac`가 그 산출물을 클래스패스에 두고 Java를 컴파일합니다.

**RN 앱 모듈은 이미 혼합 상태입니다.**

- Kotlin: `MainActivity.kt`, `MainApplication.kt`
- Java: 오토링킹이 생성하는 `PackageList.java`, New Architecture codegen 산출물

따라서 `.kt` 2개를 Java로 옮겨도 유의미한 차이가 없습니다. 서드파티는 **별도 Gradle 모듈/AAR**이라 자기 Kotlin을 자기 태스크에서 컴파일하므로 우리 모듈의 언어 선택과 무관합니다.

### 3.3 Java 변환의 실제 비용

성능이 아니라 마찰입니다.

1. **라이브러리 설치 가이드가 전부 Kotlin 기준** — 공식 문서가 `MainApplication.kt` 코드 조각을 제공하므로 매번 수동 번역
2. **업그레이드 경로 단절** — RN Upgrade Helper의 diff가 Kotlin으로 제공
3. **Kotlin 관용구 오역** — 템플릿의 `reactNativeHost`는 `by lazy`로 선언됩니다. Java에 대응 문법이 없어 직접 구현해야 하며, 필드 초기화로 옮기면 콜드 스타트가 느려집니다. 다만 영향은 경미

**Java 변환은 "위험한" 선택이 아니라 "이득 없이 번거로운" 선택입니다.** 변환 실수는 대부분 컴파일 에러로 즉시 드러나므로 조용한 실패 위험은 낮습니다. 얻는 것이 파일 2개의 문법 선호뿐이고 잃는 것이 반복 마찰이므로 Kotlin을 유지합니다.

### 3.4 폐기된 주장 — "Java 변환 시 구 아키텍처 회귀"

> 이전 검토에서 "Java 변환 시 `fabricEnabled` 플래그를 누락하면 New Architecture가 비활성화되어 구 Bridge로 회귀한다"는 주장이 있었으나 **사실이 아닙니다.**

- [**RN 0.82부터 New Architecture를 비활성화하는 옵션 자체가 제거**](https://github.com/facebook/react-native/pull/53025)되었습니다
- `newArchEnabled=false`를 설정해도 **무시되며** 경고만 표시됩니다
- iOS의 `RCT_NEW_ARCH_ENABLED=0`도 동일하게 무시됩니다
- 구 아키텍처가 필요하면 RN 0.81 또는 Expo SDK 54 이하를 써야 합니다
- RN 0.84는 여기서 더 나아가 `RCT_REMOVE_LEGACY_ARCH`를 기본 활성화해 **레거시 코드를 아예 컴파일하지 않습니다**

우리가 쓰는 RN 0.86에서는 구 아키텍처로 회귀할 방법이 없습니다. 해당 시나리오는 RN 0.68~0.81 시기에만 유효했습니다.

---

## 4. IDE 배경

결정 문서 §11은 "에디터는 개인 선택"으로 정리했습니다. 판단에 필요한 배경입니다.

### 4.1 Android Studio의 JS/TS 지원이 제한적인 이유

Android Studio는 **IntelliJ IDEA Community Edition** 기반입니다. JetBrains는 JavaScript/TypeScript 지원을 **Ultimate 전용 상용 플러그인**으로 유지하고 있어, Android Studio에는 JS/TS 언어 서비스가 포함되지 않으며 플러그인 마켓플레이스에서 설치할 수도 없습니다.

### 4.2 JetBrains 학생 라이선스가 Android Studio에 적용되는가 — 아니오

- **Android Studio는 Google 제품입니다.** JetBrains 라이선스와 무관하며, 라이선스로 기능이 해금되는 구조가 아닙니다
- Android Studio는 무료 제품이며, JS/TS 플러그인은 **Android Studio용으로 배포되지 않습니다**
- 라이선스가 있다면 Android Studio를 개선하려 하지 말고 **IntelliJ IDEA Ultimate 자체를 사용**하면 됩니다

### 4.3 선택지

**IntelliJ IDEA Ultimate**

| 기능 | 지원 |
|---|---|
| TypeScript / JSX 편집·자동완성·리팩터링 | ✅ Ultimate 포함 |
| React Native 전용 실행 구성 | ✅ Ultimate 전용 |
| React Native 디버깅 | ✅ Ultimate 전용 |
| AVD Manager / SDK Manager / Logcat | ✅ Android 플러그인 활성화 시 |
| Kotlin / Gradle | ✅ 기본 |

RN 프로젝트는 Android 프로젝트로 인식되지 않아 AVD Manager가 메뉴에 안 보일 수 있습니다. 그때는 Action 검색(`Cmd+Shift+A` / `Ctrl+Shift+A`)으로 호출합니다.

백엔드가 Spring Boot이므로 팀이 이미 IntelliJ에 익숙하다면 **백엔드와 앱을 같은 IDE에서 다룰 수 있어 도구 전환 비용이 없습니다.** 단점은 RN 생태계 자료가 VS Code 기준으로 작성된 경우가 많다는 점입니다.

**VS Code**

| 도구 | 역할 | 사용 빈도 |
|---|---|---|
| VS Code | 주 에디터. TypeScript 작성 전반 | 상시 |
| Android Studio | SDK/AVD Manager, Logcat, Gradle 문제 추적 | 초기 세팅 및 문제 발생 시 |

권장 확장: ESLint, Prettier, Expo Tools

**어느 쪽이든 Android SDK는 별도 확보가 필요합니다.** Android Studio를 한 번 설치해 SDK/NDK를 내려받고 경로를 지정하는 것이 가장 확실합니다.

---

## 5. RN에서 네이티브 코드를 새로 작성하는 경우

결정 문서 §8.2가 "직접 편집할 네이티브 소스는 0~1개"라고 판단한 기준입니다.

### 5.1 네 가지 경우

**(1) TurboModule — 네이티브 "기능"을 JS에 노출**

- **언제**: JS에서 접근할 수 없는 플랫폼 API나, Java/Kotlin으로만 제공되는 SDK를 써야 할 때
- **담는 내용**: 함수 형태의 기능. 반환값이 있고 UI가 없음
- **예시**: 기기 고유 식별자 조회, 국내 결제·본인인증 SDK 연동, 백그라운드 위치 추적 서비스
- **이 프로젝트**: 없음. 카카오 로그인·FCM 모두 기존 라이브러리가 커버

**(2) Fabric Native Component — 네이티브 "뷰"를 JS 컴포넌트로 노출**

- **언제**: 네이티브 UI 위젯을 React 컴포넌트처럼 쓰고 싶을 때
- **담는 내용**: `ViewManager`와 뷰 클래스. props를 네이티브 뷰 속성에 매핑
- **예시**: 커스텀 카메라 프리뷰, **네이티브 지도 SDK 래핑(`@rnmapbox/maps`가 하는 일이 정확히 이것)**, 고성능 차트
- **이 프로젝트**: 없음

> 참고: 결정 문서 §5.2의 [#4194](https://github.com/rnmapbox/maps/issues/4194)가 바로 이 계층의 버그입니다. rnmapbox의 27개 ViewManager가 codegen이 요구하는 `getDelegate()`를 구현하지 않아 Fabric이 리플렉션 폴백으로 떨어졌습니다. 라이브러리를 직접 만들지 않더라도 **에러 로그를 읽으려면 이 구조를 알아야 합니다.**

**(3) Application / Activity 수정 — 앱 진입점 동작 변경**

- **언제**: 앱 생명주기, 초기화 순서, 시스템 진입 동작을 바꿔야 할 때
- **담는 내용**: `MainApplication.onCreate()`의 SDK 초기화, `MainActivity`의 인텐트·딥링크·키 이벤트 처리
- **예시**: 서드파티 SDK 초기화, 딥링크 처리, 스플래시 제어, **개발자 메뉴 트리거 변경**
- **이 프로젝트**: 흔들기 Dev Menu 비활성화가 유일한 후보. 단 Expo CNG(결정 문서 §8.3)에서는 파일을 직접 고치지 않고 **custom config plugin**으로 표현합니다

**(4) 리소스 / 매니페스트 — 설정과 에셋**

- **언제**: 권한, 아이콘, 테마, 네트워크 정책 정의
- **담는 내용**: XML. 코드가 아님
- **이 프로젝트**: 있으나 **Expo config plugin이 전부 생성**하므로 직접 편집하지 않음

### 5.2 판단 기준

> **"JS에서 할 수 없는가?"** → 아니오면 네이티브를 만들지 않습니다.
> **"이미 라이브러리가 있는가?"** → 있으면 만들지 않습니다.
> **"앱 진입점의 동작을 바꿔야 하는가?"** → 예면 config plugin을 작성합니다.

**2~3주 프로젝트에서 TurboModule이나 Fabric Component를 새로 작성하는 것은 거의 항상 잘못된 판단입니다.** 라이브러리를 찾거나 요구사항을 조정하는 편이 빠릅니다.

### 5.3 `expo prebuild`가 생성하는 파일 (커밋하지 않음)

약 15~20개이며 대부분 건드릴 일이 없습니다. 결정 문서 §8.3에 따라 **저장소에 커밋하지 않습니다.**

```
frontend/android/
├── build.gradle                 프로젝트 레벨 Gradle
├── settings.gradle
├── gradle.properties            빌드 플래그
├── gradlew / gradlew.bat        Gradle Wrapper
├── gradle/wrapper/
└── app/
    ├── build.gradle             앱 모듈 Gradle
    ├── proguard-rules.pro
    ├── debug.keystore
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/.../
        │   ├── MainActivity.kt      ← Kotlin 소스 (2개뿐)
        │   └── MainApplication.kt
        └── res/
            ├── values/strings.xml
            ├── values/styles.xml
            └── mipmap-*/            아이콘
```

---

## 6. 참고 링크

**버전·릴리스**

- [Expo SDK 57 (2026-06-30, RN 0.86)](https://expo.dev/changelog/sdk-57)
- [Expo SDK 56 (2026-05-21, RN 0.85)](https://expo.dev/changelog/sdk-56)
- [React Native 0.86 (2026-06-11)](https://reactnative.dev/blog/2026/06/11/react-native-0.86)
- [React Native 0.84 — Hermes V1 기본 (2026-02-11)](https://reactnative.dev/blog/2026/02/11/react-native-0.84)
- [facebook/react-native#53025 — `newArchEnabled=false` 제거](https://github.com/facebook/react-native/pull/53025)

**지도**

- [@rnmapbox/maps 릴리스](https://github.com/rnmapbox/maps/releases)
- [@rnmapbox/maps 매니페스트 (npm)](https://registry.npmjs.org/@rnmapbox/maps/latest)
- [PR #4124 — download token 폐기](https://github.com/rnmapbox/maps/pull/4124)
- [Issue #4194 / PR #4228 — Android 16 프리즈](https://github.com/rnmapbox/maps/issues/4194)
- [Issue #4192 — onCameraChanged 폭주](https://github.com/rnmapbox/maps/issues/4192)
- [Issue #4206 — MarkerView 소실](https://github.com/rnmapbox/maps/issues/4206)

**Flutter 비교**

- [flutter/flutter#167547 — Hybrid composition 성능](https://github.com/flutter/flutter/issues/167547)

**요금**

- [Expo 요금제 — 무료 티어 Android 빌드 15회/월](https://docs.expo.dev/billing/plans/)
