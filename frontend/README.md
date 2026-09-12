# frontend — 팀원 시작 가이드

싸방팔방 Android 앱입니다. Expo SDK 57 / React Native 0.86 / Expo Router.

**이 문서의 목표:** 위에서부터 순서대로 따라 하면 Android 에뮬레이터에서 앱이 실행됩니다.
STEP 0부터 STEP 5까지 건너뛰지 말고 진행하십시오. 각 STEP 끝의 **✅ 확인**을 통과해야 다음으로 넘어갑니다.

> **필수:** 에뮬레이터는 **IntelliJ IDEA Ultimate**로 만듭니다. Android 플러그인과 Device Manager는 Ultimate 전용이라 Community Edition에서는 STEP 2를 진행할 수 없습니다.

| STEP                                      | 내용 | 소요 시간 | 반복 여부 |
|-------------------------------------------| --- | --- | --- |
| [STEP 0](#step-0-사전-준비물-설치)        | 사전 준비물 설치 | 30분 | 최초 1회 |
| [STEP 1](#step-1-에뮬레이터-만들기)       | 에뮬레이터 만들기 | 15분 | 최초 1회 |
| [STEP 2](#step-2-환경-변수-설정)          | 환경 변수(ANDROID_HOME) 설정 | 5분 | 최초 1회 |
| [STEP 3](#step-3-프로젝트-설치)           | 의존성 설치 · `.env.local` 생성 | 5분 | 최초 1회 |
| [STEP 4](#step-4-첫-빌드-실행)            | 첫 빌드 실행 | **5~10분** | 최초 1회 |
| [STEP 5](#step-5-두-번째부터의-개발-루프) | 이후 개발 루프 | 10초 | **매일** |

---

## STEP 0. 사전 준비물 설치

아래 항목을 설치합니다. 이미 있다면 버전만 확인하고 넘어가십시오.

| 항목 | 필요 버전 | 확인 방법       |
| --- | --- |-----------------|
| Node.js | 22.13.0 이상 | `node -v`       |
| JDK | **17 (고정)** | `java -version` |
| IntelliJ IDEA | **Ultimate** 최신 안정판 | 실행해서 확인   |
| Git | 최신 버전 | `git --version` |
| Android SDK | Platform 36, Build-Tools 36.0.0, Platform-Tools, Emulator | STEP 1에서 설치 |

> **JDK는 반드시 17입니다.** 다른 버전에서는 Gradle 빌드가 실패합니다.

### 0-1. Windows만: PowerShell 실행 정책 완화

`npm`과 `npx`는 PowerShell에서 `.ps1` 스크립트로 동작합니다. 기본 정책(`Restricted`)에서는 실행이 막히므로 **최초 1회** 완화합니다.

경로 상관 없이 아래 명령어 실행.
```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

### ✅ 확인

```bash
node -v          # v22.13.0 이상
java -version    # 17.x.x
```

---

## STEP 1. 에뮬레이터 만들기

IntelliJ IDEA **Ultimate**에서 진행합니다.

### 1-1. Android 플러그인 설치

**Settings → Plugins → Marketplace**에서 설치합니다. [제작자: **JetBrains s.r.o.**]

```agsl
- Android
- Android Design Tools
```

설치 후 **IntelliJ를 완전히 종료했다가 다시 켭니다.** 재시작하지 않으면 다음 단계의 메뉴가 반응하지 않습니다.

### 1-2. Android SDK 설치

```
1. File → New → Project → Android 선택.
2. SDK 설치 (디폴트로 설치 진행)
3. 설치가 완료되면 프로젝트 생성 종료.
```

### 1-3. AVD 생성

Tools → Android → Device Manager → `+` 버튼으로 가상 기기 추가.
- 만약 Device Manager를 클릭해도 반응이 없다면, 인텔리제이 종료 후 재실행.
- 아래 스팩과 같이 설정.

| 항목 | 값 |
| --- | --- |
| Device | Pixel 8 |
| API Level | API 36.0 "Baklava" · Android 16 · **Google Play Store** |
| System Image | **Windows / Intel Mac:** Google Play Intel x86_64 Atom<br>**Apple Silicon Mac:** Google Play ARM 64-v8a |

> ⚠️ 이름에 **`ATD`** 가 들어간 이미지는 자동화 테스트 전용입니다. 선택하면 에뮬레이터 화면이 검게 나옵니다.

**Finish** 후 **▶ 버튼으로 에뮬레이터를 실행**하고, 부팅되어 홈 화면이 보이는 것까지 확인합니다.

### ✅ 확인 — 반드시 하십시오

IntelliJ 화면의 표시 이름과 실제 AVD ID는 **다릅니다.** (`Pixel 8 API 36` ≠ `Pixel_8`)
**터미널에 찍히는 이름만이 정답입니다.** 이 값을 메모해 두십시오.

```bash
emulator -list-avds
```
```
Pixel_8
```

에뮬레이터를 켠 상태에서 연결도 확인합니다.

```bash
adb devices -l
```
```
List of devices attached
emulator-5554   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 ...
```

두 명령이 모두 정상 출력되어야 STEP 4가 성공합니다. 실패하면 → [문제 해결: `emulator` 명령을 찾을 수 없음](#emulator-명령을-찾을-수-없음)

---

## STEP 2. 환경 변수 설정

Android SDK 경로를 OS에 등록합니다. **`emulator`와 `platform-tools` 두 폴더를 모두 `PATH`에 넣어야 합니다.**
`platform-tools`만 넣으면 `adb`는 되지만 Expo가 AVD 목록을 조회하지 못해 STEP 4에서 `Could not find device with name` 오류가 납니다.

<details open>
<summary><b>Windows</b></summary>

먼저 최초 실행시, 아래 명령어를 통해 이번 세션에만 임시로 적용.

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\emulator;$env:ANDROID_HOME\platform-tools"
```
설정 후 **기존 터미널을 모두 닫고 새 PowerShell을 엽니다.**

- 이후 모든 최초 실행 프로세스가 완료되면,
**시스템 속성 → 고급 → 환경 변수**에서 아래 3개를 등록합니다. (영구 설정 — 권장)

| 구분 | 변수명 | 값 |
| --- | --- | --- |
| 사용자 변수 → 새로 만들기 | `ANDROID_HOME` | `%LOCALAPPDATA%\Android\Sdk` |
| 사용자 변수 `Path` → 편집 → 새로 만들기 | — | `%ANDROID_HOME%\emulator` |
| 사용자 변수 `Path` → 편집 → 새로 만들기 | — | `%ANDROID_HOME%\platform-tools` |


</details>

<details open>
<summary><b>macOS</b></summary>

`~/.zshrc`에 아래 두 줄을 추가하고 터미널을 새로 엽니다.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
```

</details>

> SDK가 아직 없어서 폴더가 비어 있어도 괜찮습니다. STEP 2에서 이 경로에 설치됩니다.
> `ANDROID_SDK_ROOT`는 deprecated된 변수입니다. 비어 있어도 `ANDROID_HOME`만 잡혀 있으면 정상입니다.

### ✅ 확인

새 터미널에서 실행합니다. SDK 설치 전이라면 실패가 정상이며, STEP 1를 마친 뒤 다시 확인합니다.

```bash
adb version
```

---

## STEP 3. 프로젝트 설치

### 3-1. 의존성 설치

저장소에 `package-lock.json`이 있으므로 **`npm install`이 아니라 `npm ci`** 를 사용합니다.

```bash
# macOS

cd /path/to/S15P11A701/frontend
npm ci
```
```powershell
# Windows PowerShell

Set-Location C:\path\to\S15P11A701\frontend
npm ci
```

> 의존성을 **새로 추가·갱신할 때만** `npm install`을 쓰고, 변경된 `package-lock.json`을 함께 커밋합니다.

### 3-2. `.env.local` 생성

```bash
# macOS
cp .env.example .env.local
```
```powershell
# Windows PowerShell
Copy-Item .env.example .env.local
```

`.env.local`을 열어 값을 채웁니다. (프로젝트 초기 정상 실행 확인 후에 해도 무방)

| 변수 | 용도 | **에뮬레이터 개발용 값** |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | HTTP API 주소 | `http://10.0.2.2:8080` |
| `EXPO_PUBLIC_WS_URL` | STOMP WebSocket 주소 | `ws://10.0.2.2:8080/ws` |
| `EXPO_PUBLIC_MAPBOX_TOKEN` | Mapbox Public Token (`pk.`로 시작) | 팀에서 발급한 토큰 |
| `EXPO_PUBLIC_KAKAO_APP_KEY` | 카카오 네이티브 앱 키 | 없으면 카카오 로그인만 불가 |
| `GOOGLE_SERVICES_JSON` | FCM 설정 파일 경로 | 없으면 푸시 없이 빌드됨 |

> ⚠️ **`.env.example`의 기본값은 `192.168.0.10`이라 에뮬레이터에서 동작하지 않습니다. 반드시 `10.0.2.2`로 바꾸십시오.**
> Android 에뮬레이터에서 `localhost`는 개발 PC가 아니라 **에뮬레이터 자신**을 가리킵니다. 개발 PC를 가리키는 주소가 `10.0.2.2`입니다. (실기기에서는 개발 PC의 LAN IP를 사용합니다.)

> `.env.local`은 Git에 올리지 않습니다. `EXPO_PUBLIC_`으로 시작하는 값은 앱 번들에 포함되므로 **비밀값을 넣으면 안 됩니다.**

### ✅ 확인

```bash
npm run typecheck
```

---

## STEP 4. 첫 빌드 실행

이 프로젝트는 Expo Go가 아니라 **development build**로 실행합니다. Mapbox·카카오 로그인 같은 네이티브 모듈을 쓰기 때문입니다.

### 4-1. 에뮬레이터를 먼저 켭니다

IntelliJ **Device Manager**에서 `Pixel 8`을 ▶로 실행하고, 홈 화면이 보일 때까지 기다립니다.

### 4-2. 빌드 · 설치 · 실행

`frontend` 폴더에서 아래 명령을 순서대로 실행합니다.

```bash
# macOS
GOOGLE_SERVICES_JSON=./app/google-services.json npm run prebuild
npx expo run:android    # 빌드 → 설치 → 실행
```
```powershell
# Windows PowerShell
$env:GOOGLE_SERVICES_JSON = "./app/google-services.json"
npm run prebuild
$env:EXPO_DEV_ABI = "x86_64"   # 빌드 시간 단축 (선택)
npx expo run:android
```

> **FCM 설정 파일**: 원본은 `frontend/app/google-services.json`에 보관합니다. `GOOGLE_SERVICES_JSON`을 지정하면 `app.config.ts`의 `android.googleServicesFile` 설정이 Google Services Gradle 플러그인을 구성하고, `prebuild` 중 Android Gradle이 읽는 `frontend/android/app/google-services.json`으로 자동 복사합니다. `frontend/android/google-services.json`이 아니라 반드시 `android/app/` 경로를 사용합니다. 매번 셸에서 설정하지 않으려면 `frontend/.env.local`에 `GOOGLE_SERVICES_JSON=./app/google-services.json`을 추가합니다.

> **⏱️ 5~10분 걸립니다. 정상입니다.** 라이브러리 다운로드가 아니라 네이티브 컴파일입니다.
> New Architecture가 켜져 있어 네이티브 모듈 35개마다 코드 생성과 C++ 빌드가 일어나고, 이를 ABI 4종에 대해 반복합니다.
>
> **x86_64만 확인해도 무방**하니 `$env:EXPO_DEV_ABI = "x86_64"`를 설정하고 빌드해도 상관없음.

> **`--device`는 에뮬레이터 여러 대를 띄웠을 때만 지정.** 하나만 켜져 있으면 Expo가 알아서 잡습니다. 
> ```bash
> npx expo run:android --device Pixel_8        # STEP 2 ✅확인의 AVD 이름
> npx expo run:android --device emulator-5554  # ADB 시리얼 — 둘 다 동작
> ```

### ✅ 확인 — 성공 판정

에뮬레이터에서 다음 두 가지가 보이면 성공입니다.

1. **"싸방팔방 버전"** 팝업이 뜬다
2. 화면 중앙에 흰 배경으로 **`Hello world`** 가 보인다

여기까지 확인되면 에뮬레이터 · APK 설치 · Metro 연결이 모두 정상입니다. 🎉

---

## STEP 5. 개발 루프

### JS/TS만 고쳤다면 `npx expo run:android`를 돌리지 마십시오

`npx expo run:android`는 **네이티브 빌드**입니다. JS/TS만 고쳤다면,

```bash
npm start
```

이미 설치된 개발용 앱을 에뮬레이터에서 열면 Metro에 연결되고 Fast Refresh가 적용됩니다. **초 단위로 붙습니다.**

### 변경 내용별로 해야 할 일

| 무엇을 바꿨나 | 해야 할 일 |
| --- | --- |
| `app/`, `src/`의 JS/TS/스타일 | `npm start` → Fast Refresh (**대부분 이 경우**) |
| `.env.local`의 `EXPO_PUBLIC_` 값 | Metro 재시작 → `npm start -- --clear` |
| `app.config.ts`, 네이티브 모듈, 카카오 키, FCM 파일 | `npm run prebuild` → `npx expo run:android` |

네이티브 빌드가 필요한 경우는 **표의 마지막 줄뿐입니다.**

> `frontend/android/`는 Expo prebuild 산출물입니다. **직접 수정하지 말고** 네이티브 설정은 `app.config.ts` 또는 config plugin으로 관리합니다.

### 커밋 전 품질 확인

```bash
npm run typecheck
npm run lint
npm run doctor
```

네이티브 빌드 없이 JS 번들만 빠르게 검증하려면:

```bash
npx expo export --platform android --output-dir ./.expo/export
```

---

## 문제 해결

증상을 찾아 순서대로 시도하십시오.

### `Could not find device with name: ...`

AVD 이름이 틀렸거나, `emulator`가 `PATH`에 없어 Expo가 AVD 목록을 조회하지 못하는 경우입니다.

1. **가장 간단한 해결:** 에뮬레이터가 이미 켜져 있다면 `--device` 옵션을 **아예 빼십시오.**
2. 그래도 안 되면 진단합니다.
   ```bash
   emulator -list-avds   # 실패 → PATH 문제, STEP 1로
   adb devices -l        # 에뮬레이터가 device 상태인지
   ```
3. `emulator -list-avds`가 출력한 문자열을 **그대로 복사**해서 쓰십시오. IntelliJ가 `Pixel 8 API 36`으로 보여줘도 실제 ID는 `Pixel_8`입니다. **공백이 들어간 이름은 절대 매칭되지 않습니다.**

### `emulator` 명령을 찾을 수 없음

`PATH`에 `%ANDROID_HOME%\emulator`가 빠졌습니다. STEP 1을 다시 하고 **새 터미널을 여십시오.** 기존 터미널에는 반영되지 않습니다.

### `SDK location not found`

`ANDROID_HOME`이 없거나 SDK 경로가 다릅니다.

```bash
# macOS
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
```
```powershell
# Windows PowerShell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\emulator;$env:ANDROID_HOME\platform-tools"
adb version
```

기본 경로에 SDK가 없다면 IntelliJ **SDK Manager**에 표시되는 Android SDK Location을 쓰십시오.

### `Tools → Android → Device Manager`가 없거나 눌러도 반응 없음

- **메뉴 자체가 없음** → Community Edition입니다. Android 플러그인은 Ultimate 전용이므로 **Ultimate가 필요합니다.**
- **메뉴는 있는데 반응 없음** → 플러그인 설치 후 IntelliJ를 **완전히 종료했다가** 다시 켜십시오.

### 에뮬레이터 화면이 검은색

**ATD(Android Test Device) 이미지**를 쓰고 있을 가능성이 큽니다. 자동화 테스트 전용이라 화면이 나오지 않습니다.
STEP 2-3의 표대로 **Pixel 8 / API 36 / Google Play** 이미지로 다시 만드십시오. Windows는 `x86_64`, Apple Silicon Mac은 `ARM 64-v8a`인지도 확인합니다.

### 빌드가 10분씩 걸림

**첫 빌드는 정상입니다.** 네이티브 모듈 35개 × ABI 4종의 코드 생성과 C++ 빌드입니다.

문제는 **매번 빌드하는 습관**입니다. JS/TS만 고쳤다면 `npm start`로 충분합니다 → [STEP 5](#step-5-두-번째부터의-개발-루프)

Windows에서는 Defender 실시간 검사 **제외 경로**에 프로젝트 폴더와 `%USERPROFILE%\.gradle`을 추가하면 체감이 개선됩니다.

### Metro가 이전 코드를 표시함

캐시를 비우고 개발 서버를 다시 시작합니다.

```bash
npx expo start --dev-client --clear
```

### API 요청이 모두 실패함

1. `.env.local`에 `localhost`를 쓰지 않았는지 확인합니다. 에뮬레이터에서는 **`10.0.2.2`** 여야 합니다.
   - `EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080`
   - `EXPO_PUBLIC_WS_URL=ws://10.0.2.2:8080/ws`
2. 값을 고쳤다면 Metro를 재시작합니다. → `npm start -- --clear`
3. 백엔드가 켜져 있는지 확인합니다. → [LOCAL_DEVELOPMENT.md](../docs/LOCAL_DEVELOPMENT.md)

### `npm run prebuild --device Pixel_8`이 실패함

`expo prebuild`는 `android/` 폴더를 만드는 명령이라 기기와 무관하며 **`--device` 옵션 자체가 없습니다.**

또한 npm 스크립트에 인자를 넘기려면 **`--`로 구분**해야 합니다. 빠뜨리면 npm이 플래그를 자기 설정으로 먹고 값만 위치 인자로 넘깁니다.

```powershell
npm run prebuild --device Pixel_8
# → expo prebuild --platform android Pixel_8
# → Invalid project root: ...\frontend\Pixel_8

npm run prebuild -- --clean   # ✅ 올바른 형태
```

### 그 외 — 네이티브 설정이 꼬였을 때 (최후의 수단)

먼저 캐시를 지운 Metro(`npx expo start --dev-client --clear`)를 시도하고, 그래도 안 되면 생성물을 다시 만듭니다.

```bash
npm run prebuild -- --clean
npx expo run:android
```

---

## 참고 문서

| 문서 | 내용 |
| --- | --- |
| [LOCAL_DEVELOPMENT.md](../docs/LOCAL_DEVELOPMENT.md) | 로컬 백엔드 · 인프라 실행 |
| [ANDROID_SETUP.md](../docs/ANDROID_SETUP.md) | Android 초기 설정 상세 |
| [ANDROID_STACK_DECISION.md](../docs/ANDROID_STACK_DECISION.md) | Android/Expo 기술 선택 근거 |
| [ANDROID_BACKGROUND.md](../docs/ANDROID_BACKGROUND.md) | 백그라운드 동작 관련 |
| [FRONTEND_SETUP_VERIFICATION.md](../docs/FRONTEND_SETUP_VERIFICATION.md) | 프론트엔드 세팅 검증 기록 |
