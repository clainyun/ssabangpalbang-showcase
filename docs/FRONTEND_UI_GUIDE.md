# 프론트엔드 UI 구조 안내

새로 합류하는 프론트엔드 팀원을 위한 문서입니다. 지금까지 회원가입/로그인/스플래시 화면을 만들면서 잡힌 UI 톤앤매너와, 다른 화면(프로필, 게시글 작성 등)을 만들 때 재사용할 수 있는 공용 컴포넌트를 정리했습니다.

## 1. 폴더 구조

화면/컴포넌트를 새로 만들 때 어디에 둘지는 아래 규칙을 따릅니다.

- **`app/`** — 파일 기반 라우팅(Expo Router). 실제 화면 파일이 여기 있습니다.
  - `app/(auth)/` — 로그인 전 화면 (로그인, 회원가입, 온보딩, 소셜 로그인 콜백)
  - `app/(app)/(tabs)/` — 로그인 후 하단 탭 5개 화면 (지도, 커뮤니티, 홈, 찜, 마이)
  - `app/(app)/` 아래 탭 밖 파일들 — 탭에서 눌러 들어가는 상세 화면 (아파트 상세, 임장, 게시글, 스터디, 채팅 등)
- **`src/components/`** — 여러 화면에서 재사용하는 순수 UI 컴포넌트 (`FormField`, `TabIcon` 등). 특정 도메인 로직에 종속되면 안 됩니다. 반대로 한 화면에서만 쓰는 컴포넌트(예: signup/login의 `BackButton`)는 그냥 그 화면 파일 안에 로컬로 둬도 됩니다.
- **`src/constants/`** — 색상 등 앱 전역 상수 (`colors.ts`).
- **`src/features/<도메인>/`** — 도메인별 로직. 지금은 주로 `api/` 하위에 백엔드 호출 함수를 둡니다 (`features/auth/api/login.ts` 등). 화면(`app/`)에서 이 함수들을 import해서 씁니다.
- **`src/store/`** — zustand 전역 상태 (인증, 캐릭터 등 저빈도 클라이언트 상태만 — 서버 데이터는 여기 넣지 않고 TanStack Query가 담당).
- **`src/lib/`** — 환경변수, 토큰 저장소, 지도 초기화 등 순수 유틸.
- `src/api/` 폴더도 있는데 지금은 비어있습니다(`.gitkeep`만 존재) — 실제 API 호출은 전부 `src/features/<도메인>/api/` 쪽에 있으니 헷갈리지 마세요.

새 화면을 만든다면 이 순서로 진행하면 됩니다:
1. `app/` 아래 알맞은 라우트 그룹에 화면 파일 추가 (인증 필요 여부에 따라 `(auth)` vs `(app)`)
2. 그 화면 전용 API 호출이 필요하면 `src/features/<도메인>/api/`에 함수 추가 (`signup.ts`/`login.ts` 패턴 참고)
3. 여러 화면에서 재사용할 UI면 `src/components/`에, 그 화면 전용이면 화면 파일 안에 로컬로/

## 2. 공용 컴포넌트 (`src/components/`)

### `FormField.tsx`

라벨 + 입력창 + 에러 메시지를 묶은 입력 필드 컴포넌트입니다. `signup.tsx`, `login.tsx`에서 실제로 쓰고 있어요.

```tsx
import { FormField } from '@/components/FormField';

<FormField
  label="이메일"
  value={email}
  onChangeText={setEmail}
  error={fieldErrors.email}          // 있으면 빨간 테두리 + 에러 텍스트, 없으면 평소 스타일
  keyboardType="email-address"
  placeholder="example@email.com"
  autoComplete="email"               // 자동완성/비밀번호 관리자 연동 (아래 4번 참고)
  textContentType="username"
  returnKeyType="next"
  onSubmitEditing={() => nextFieldRef.current?.focus()}
/>
```

- `ref`를 받을 수 있어서(`forwardRef`), 여러 필드를 이어붙일 때 키보드 "다음" 버튼으로 다음 필드에 포커스를 옮길 수 있습니다 (아래 4번 참고).
- `error` prop이 있을 때와 없을 때 필드 자체의 높이는 똑같이 유지되도록 만들어져 있지 않습니다 — 에러 텍스트가 나타나면 그만큼 아래 요소가 밀립니다. 화면 전체의 필드 간격(`gap`)으로 리듬을 맞추는 방식이니, 새 화면에서도 필드들을 감싸는 `View`에 `gap`을 주는 패턴을 따르면 됩니다.

### `TabIcon.tsx`

하단 탭 바 아이콘 컴포넌트. `app/(app)/(tabs)/_layout.tsx`에서 `tabBarButton`을 완전히 대체하는 방식으로 씁니다 (React Navigation 기본 아이콘 박스 크기와 안 맞아서 이렇게 우회했음 — 파일 상단 주석 참고).

## 3. 색상 (`src/constants/colors.ts`)

화면에서 색을 하드코딩하지 말고 여기서 가져다 씁니다.

| 이름 | 값 | 용도 |
|---|---|---|
| `PRIMARY_COLOR` | `#13B26E` | 브랜드 그린 — 버튼 텍스트, 링크, 강조 텍스트 |
| `BUTTON_BACKGROUND_COLOR` | `#DDF6E9` | 연한 민트 — 주요 버튼(가입하기/로그인) 배경 |
| `ERROR_COLOR` | `#E1483F` | 에러 텍스트/테두리 |
| `ERROR_BACKGROUND_COLOR` | `#FDECEC` | 에러 박스 배경 |
| `TEXT_COLOR` | `#1F2937` | 본문/타이틀 텍스트 |
| `LABEL_COLOR` | `#374151` | 필드 라벨 텍스트 |
| `PLACEHOLDER_COLOR` | `#AEBAB2` | placeholder, 서브텍스트, 비활성 텍스트 |



## 4. 화면 구성 패턴 (auth 화면 기준)

`signup.tsx`/`login.tsx`를 새 화면의 템플릿으로 참고하면 됩니다. 공통 구조:

```tsx
<LinearGradient colors={GRADIENT_COLORS} locations={GRADIENT_LOCATIONS} style={styles.flex}>
  <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
    <ScrollView style={styles.scroll} contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      {/* 타이틀 + 서브텍스트 + FormField들 + 폼 레벨 에러 */}
    </ScrollView>

    {/* 하단 고정 영역: 스크롤과 분리된 제출 버튼 */}
    <View style={styles.bottomBar}>
      <Pressable style={styles.submitButton}>...</Pressable>
    </View>
  </KeyboardAvoidingView>
</LinearGradient>
```

- **배경 그라데이션**: 상단만 연한 그린(`#E5F7ED`)이고 90% 지점부터 흰색 — 화면 전체가 흰색에 가깝게 보이도록 의도한 값입니다. 두 화면에 동일하게 복붙돼 있는데, 화면이 하나 더 늘어나면(이번에 만드실 프로필/게시글 화면 등) `colors.ts`처럼 공용 상수로 뺄 시점일 수 있습니다.
- **하단 고정 버튼**: 제출 버튼은 `ScrollView` 안에 넣지 않고 `KeyboardAvoidingView`의 형제로 빼서, 필드가 몇 개든 화면 맨 아래 고정되게 합니다.
- **키보드 "다음/완료" 체이닝**: 여러 입력 필드가 있으면 각 필드에 `useRef<TextInput>`을 만들고, `onSubmitEditing`으로 다음 필드에 `.focus()`를 호출하도록 연결합니다. 마지막 필드는 `returnKeyType="done"` + `onSubmitEditing={handleSubmit}`.
- **자동완성**: 이메일류 필드는 `autoComplete="email"` + `textContentType="username"`(주의: `emailAddress`가 아니라 `username`이어야 iOS에서 비밀번호와 짝지어 인식됨). 회원가입 비밀번호는 `new-password`/`newPassword`, 로그인 비밀번호는 `password` — 새 계정 생성이냐 기존 계정 로그인이냐에 따라 다른 값을 씁니다.


## 5. 하단 탭 바 & 시스템 내비게이션 바

`app/(app)/(tabs)/_layout.tsx`의 탭 바는 `useSafeAreaInsets()`로 안드로이드 시스템 내비게이션 바(제스처 바 vs 3버튼 바) 높이에 맞춰 `marginBottom`을 실시간으로 계산합니다. 3버튼 내비게이션(갤럭시 등)에서 탭 바가 시스템 버튼에 가리는 문제를 이걸로 고쳤어요. 새로 화면을 만들 때 하단에 고정 요소를 둔다면 이 패턴을 참고하세요.

## 6. 아직 임시로 남아있는 것들 (정리 필요)

- `app/(app)/(tabs)/home.tsx`, `app/(auth)/login.tsx`: 카카오 로그인 연동 전까지 쓰는 더미 로그인/로그아웃 버튼. `TODO` 주석으로 표시돼 있음 — 실제 소셜 로그인 붙으면 제거.
- `app/(auth)/onboarding.tsx`: 완전한 플레이스홀더. 캐릭터 3종 선택 + 개인 조건 입력 온보딩이 아직 구현 안 됨 (FE-002 요구사항 중 남은 부분).
- `GRADIENT_COLORS`/`GRADIENT_LOCATIONS`, `EMAIL_REGEX` 등은 아직 `signup.tsx`/`login.tsx`에 각각 복붙돼 있음 — `FormField`/`colors.ts`만 우선 뺐고, 나머지는 온보딩 화면까지 나온 뒤 공용 컴포넌트로 뺄지 판단하기로 함.

## 7. 새 화면 만들 때 체크리스트

1. `app/` 아래 알맞은 라우트 그룹에 화면 파일 위치 정하기 (1번 폴더 구조 참고)
2. `FormField`, `colors.ts` 상수 재사용
3. 입력 폼이 있다면 키보드 다음/완료 체이닝 + 자동완성 속성 챙기기
4. 제출 버튼은 하단 고정(`bottomBar` 패턴)으로 스크롤 영역과 분리
5. 새로운 색상이 필요하면 화면에 하드코딩하지 말고 `colors.ts`에 추가하고 이름 붙이기
6. 임시/TODO 코드를 넣는다면 주석으로 명확히 표시 (`// TODO: 임시 - ...`)
