# GMS 3개 모델 프로젝트 적합성 평가

이 평가는 Gemini 3.5 Flash, GPT-5.4 mini, Claude Sonnet 4.6에 같은 합성 근거와 같은 출력 계약을 주고 프로젝트 챗봇에 대한 적합성을 비교한다. 특정 모델의 1위를 보장하지 않으며, 데이터셋·정책·게이트·가중치는 실행 전에 파일과 SHA-256으로 고정한다.

v1은 파일럿, v2는 네이티브 구조화 출력을 끈 raw JSON 스트레스 테스트로 보존한다. 실제 모델 선정 근거에는 운영 어댑터를 사용하는 v3 결과만 사용한다.

| 버전 | 목적 | 공식 선정 사용 |
|---|---|:---:|
| v1 | 초기 12문항 파일럿 | 아니요 |
| v2 | 동일 strict raw JSON 출력 스트레스 | 아니요 |
| v3 | 공급자별 운영 구조화 출력 경로 비교 | 예 |

## 공식 v3 production-fit 프로파일

- 모델: `gemini-3.5-flash`, `gpt-5.4-mini`, `claude-sonnet-4-6`
- 데이터: 합성 한국어 20문항
  - 직접 리포트 4, 장문 리포트 6, 웹 3, 근거 부족 3, 프로필 2, 리포트·웹 혼합 2
  - 장문은 8k, 20k, 29k 근거를 각각 2문항씩 사용하며 정답 위치를 앞·중간·뒤로 분산
- 반복: 문항당 3회, 모델당 측정 60회
- 워밍업: 모델당 1회, 통계 제외
- 총 호출: `20 × 3 × 3 + 3 = 183`
- 생성 설정: temperature 0.2, 모델 재시도와 외부 재시도 모두 0, Claude `max_tokens=4096`
- 실행 순서: 전 모델이 한 요청 lane을 공유하고 요청 시작 간격을 최소 6.5초로 제한
- 모델 순서: 3×3 Latin square 순환으로 각 모델이 60개 배치 중 20번씩 첫 번째가 됨
- 워밍업 후 측정 전 cooldown: 300초
- 공급자 모드:
  - Gemini: JSON MIME + response schema
  - GPT: `response_format=json_object` + `developer` role
  - Claude: Messages API + JSON-only prompt
- 최종 검증: 세 공급자 모두 같은 JSON Schema와 `LlmAnswerPayload` 적용

정책은 [evaluation_policy_v3.json](../ai/app/evaluation/evaluation_policy_v3.json), 데이터는 [chatbot_grounding_v3.json](../ai/app/evaluation/datasets/chatbot_grounding_v3.json)에 있다. 공식 모드는 dataset, 20개 case별 prompt manifest, 공통 schema, evaluator 구현의 SHA-256과 모델 ID, provider mode, 횟수, seed, max tokens, pacing을 외부 호출 전에 검증한다.

## 공급자 결과 정규화

각 공급자의 운영 어댑터가 네이티브 JSON 응답을 파싱한 뒤 공통 schema로 검증한다. HTTP는 2xx만 성공으로 인정하며 3xx도 실패다.

| Outcome | 의미 |
|---|---|
| `transport` | timeout, 연결 오류, 429 외 HTTP 오류, 공급자 응답·생성 실패 |
| `rate-limit` | HTTP 429 |
| `schema` | 운영 어댑터 파싱 뒤 필드·타입·추가 필드가 공통 계약과 다름 |
| `success` | 공통 schema까지 통과 |

v3는 실제 배포 경로의 효과를 비교하므로 공급자별 지원 기능과 운영 파서를 사용한다. raw JSON 형식 준수 자체를 비교해야 한다면 v2를 별도 스트레스 결과로 해석한다.

## 채점 규칙

답변 가능한 문항은 질문의 주어를 반복하도록 요구하지 않고 필요한 사실값만 확인한다. 각 fact group에서 동의 가능한 표현 하나 이상이 있어야 하며, 금지된 상충 사실은 없어야 한다. 숫자는 토큰 경계를 확인하므로 `167.4%`를 `67.4%` 정답으로 처리하지 않는다.

근거 부족 문항은 다음을 모두 만족해야 한다.

- 사전 등록한 거절 표현 중 하나를 포함
- 해당 거절 표현이 “확인할 수 없는 것이 아니다”처럼 부정되지 않음
- 사전 등록한 구체적 추측 표현을 포함하지 않음
- `usedSources`가 빈 배열

근거 선택은 후처리 전 raw `usedSources` 집합을 골든 집합과 exact-set으로 비교한다. 리포트와 웹 번호를 함께 선택하면 혼합 인용 위반이다.

## 분모와 지표

가용성과 의미 품질을 분리한다.

| 지표 | 분모 |
|---|---|
| 완료율·실패율·outcome별 실패 | 예약된 전체 60회/모델 |
| 사실·거절·근거·effective-task 조건부 정확도 | 공통 schema까지 성공한 해당 표본 |
| effective-task case macro | 각 case의 예약된 3회 중 effective-task 성공률을 구한 뒤 20개 case를 동일 가중 평균 |
| 장문 task success | 예약된 장문 18회; 사실과 exact source가 모두 맞아야 성공 |
| 근거 없는 답변 위반율 | 성공한 근거 부족 표본 |
| 혼합 인용 위반율 | 성공했고 리포트·웹 근거가 모두 제공된 표본 |
| p50/p95 | 성공 응답의 운영 provider 호출·파싱 시간, nearest-rank; 항상 `n` 병기 |

기술 실패를 조건부 품질 분모에서 제외하되 완료율, case macro, 장문 task success, safe/mixed completion에는 그대로 남긴다. 모든 비율은 분자/분모를 함께 기록한다. warmup 또는 측정에서 HTTP 429가 한 번이라도 관측되면 `quality_comparison_valid=false`로 기록하고 leaderboard를 만들지 않는다.

## 사전 등록 게이트와 점수

다음 조건을 모두 통과한 모델만 ProjectFitScore를 계산한다.

- 완료율 ≥ 95%
- effective-task case macro ≥ 90%
- 장문 정확도 ≥ 85%
- 근거 없는 답변 위반율 = 0%
- 혼합 인용 위반율 = 0%
- 안전·혼합 인용 준수율 평균 ≥ 95%
- 성공 응답 p95 < 20초

점수는 비용을 추정하지 않으며 다음 공식만 사용한다.

```text
100 × (
  0.50 × effective-task case macro
  + 0.25 × long accuracy
  + 0.15 × mean(safety compliance, mixed-citation compliance)
  + 0.10 × fastest eligible p95 / model p95
)
```

## 실행

`infra/.env.local`에 GMS 키와 세 endpoint가 설정되어 있어야 한다. 키나 endpoint를 명령 인자로 넣지 않는다.

```powershell
cd ai
python -m app.evaluation.chatbot_model_benchmark `
  --official `
  --env-file ../infra/.env.local `
  --output-dir ../output/model-eval
```

네트워크 없는 검증:

```powershell
cd ai
python -m pytest -q tests/test_chatbot_model_benchmark_v3.py tests/test_gms_model_suite.py
python -m pytest -q
```

## 결과와 보안

완료된 결과는 `output/model-eval/<UTC-run-id>/`에 원자적으로 공개된다. 먼저 exact 모델×case×반복 행렬과 summary 재집계 결과를 검증하고, 숨김 staging 디렉터리에 기록한 뒤 checksum과 `COMPLETE`를 생성해 최종 디렉터리로 rename한다. 중복·누락·알 수 없는 셀, summary 불일치, 예상하지 못한 구현 예외가 있으면 최종 run 디렉터리를 만들지 않는다.

- `summary.json`: 정책·데이터 해시, 분자·분모, 게이트, 점수와 leaderboard 유효성
- `summary.csv`: 장표용 핵심 지표와 분자·분모
- `summary.md`: 사람이 읽는 비교표와 해석 제한
- `samples.jsonl`: case/model/repetition, outcome, 지연시간, 근거 번호와 boolean 채점 결과
- `checksums.sha256`: 위 네 파일의 SHA-256
- `COMPLETE`: checksum manifest의 SHA-256 완료 표식

GMS 키, endpoint, HTTP header, system/user prompt, 합성 근거 원문, 모델 답변 원문, 예외 메시지는 저장하지 않는다.

## 해석 한계

- 합성 20문항은 실제 사용자 질문 전체를 대표하지 않는다.
- p95는 GMS 운영 provider 호출·파싱·공통 schema 검증 구간만 포함한다. DB, 임베딩, RAG 검색, 실제 웹 검색, 전체 챗봇 API 후처리, pacing/cooldown은 제외된다.
- 모델당 성공 표본이 최대 60개이므로 운영 SLA 추정치가 아니다.
- 공용 GMS 게이트웨이 상태가 완료율과 지연시간에 영향을 줄 수 있다.

## 체크리스트 선택 2개 모델 평가 v1

이 프로필은 챗봇 평가와 별도로, 운영 catalog 질문 중 최종 체크리스트
`itemCode`를 고르는 `/internal/v1/checklists/select` 경로를 비교한다.

- 모델: `gemini-3.5-flash`, `gpt-5.4-nano`
- 입력: `ssabang_field_visit_checklist_raw_v3_300.json`의 300개 문항
- 사례: LIVE/INVEST/LEARN, 8개 우선순위, 차량·자녀 조건을 교차한 12개
- 반복: case당 3회, 모델당 측정 36회
- 워밍업: 모델당 1회
- 총 호출: `12 × 3 × 2 + 2 = 74`
- 설정: temperature 0.2, 재시도 0, 요청 시작 간격 6.5초, 워밍업 후 60초 대기
- 응답 계약: 현재 운영 기준 20~30개, 목표 25개

평가기의 filter, server score, shortlist 구성은 백엔드
`fieldvisit/catalog` 구현을 결정적으로 재현한다. `catalog-shortlist-size=50`은
상한이며 카테고리당 6개·선택 우선순위당 3개 제한 때문에 실제 case별
shortlist는 27~50개다. 모두 목표 25개 이상이어야 평가를 시작한다.

### 지표

| 지표 | 의미 |
|---|---|
| response/schema/operational pass | 공급자 응답, FastAPI schema, Spring 정책까지 단계별 성공률 |
| target/common/SUM | 25개 정확 일치, 공통 핵심과 총평 포함 비율 |
| priority alignment | 선택 우선순위마다 권장 2개를 채운 비율의 macro 평균 |
| purpose alignment | 목적 또는 `ALL` tag 항목의 달성 가능한 최대 대비 선택 비율 |
| server-score NDCG | 백엔드 점수 순위 대비 모델 선택 순서의 NDCG |
| category diversity | case에서 가능한 category와 목표 10개 대비 다양성 |
| p50/p95 | 운영 provider 호출·파싱 성공 응답 시간과 표본 수 |

종합점수는 operational pass 30%, priority 25%, purpose 15%, NDCG 15%,
diversity 10%, target 일치 5%다. schema·정책·호출 실패 표본은 0점으로
전체 예정 표본 분모에 남는다. 워밍업 또는 측정 중 HTTP 429가 하나라도
있으면 세부 지표는 남기지만 leaderboard는 만들지 않는다.

### 실행

`infra/.env.local`에는 `GMS_KEY`, `GMS_GEMINI_ENDPOINT_URL`,
`GMS_OPENAI_ENDPOINT_URL`이 필요하다. Claude 설정은 필요 없으며 모델 ID는
정책에서 고정한다. 이 구성 작업에서는 live GMS 호출을 실행하지 않았다.

```powershell
cd ai
python -m app.evaluation.checklist_model_benchmark `
  --official `
  --env-file ../infra/.env.local `
  --output-dir ../output/checklist-model-eval
```

네트워크 없는 검증:

```powershell
cd ai
python -m pytest -q tests/test_checklist_model_benchmark.py `
  tests/test_gms_model_suite.py tests/test_checklist_select.py
```

결과는 `output/checklist-model-eval/<UTC-run-id>/`에 챗봇 평가와 같은
`samples.jsonl`, `summary.json/csv/md`, `checksums.sha256`, `COMPLETE` 구조로
원자적으로 기록한다. itemCode와 점수만 보관하고 key, endpoint, header,
prompt, provider raw envelope, 예외 메시지는 저장하지 않는다.

이 평가는 사람의 골든 체크리스트가 아니라 현재 서버 정책과 점수에 대한
정렬 품질을 측정한다. 따라서 최종 모델 선정 전에는 실제 사용자 시나리오의
수동 품질 검토를 함께 수행해야 한다.

## 도곡렉슬 hybrid E2E v3

이 프로필은 v1/v2/v3 GMS 모델 비교와 별개다. 배포 PostgreSQL을 읽기 전용으로
연결하고 운영 `ChatbotAnswerService`, 공용 query embedder, pgvector 리포트 검색,
설정된 live 웹 provider를 사용한다. LLM 경계만 deterministic scripted provider로
대체한다. 따라서 결과는 **GMS나 실제 모델 품질을 측정하지 않으며** GMS client를
생성하지 않는다.

대상은 단지코드 `A13527203`, 이름 `도곡 렉슬`로 고정한다. 숫자 apartment/report
ID는 preflight에서 읽기 전용 `SELECT`로 해석하며 데이터셋에 고정하지 않는다.
preflight는 `도곡렉슬 임장 기록 #1..#3` 리포트가 모두 `DONE`이고 embedding된
chunk가 존재하는지, 공용 embedder가 768차원 sentinel query를 생성하는지, 필수
profile 필드와 live 웹 provider가 준비됐는지 확인한다. 하나라도 실패하면 첫
문항과 첫 웹 검색 전에 종료한다.

실행에는 배포 DB 읽기와 live 웹 사용을 각각 명시적으로 확인해야 한다.

```powershell
cd ai
python -m app.evaluation.chatbot_model_benchmark `
  --e2e-dogok-rexle `
  --llm-mode scripted `
  --allow-deployment-db-read `
  --allow-live-web `
  --env-file ../infra/.env.local `
  --output-dir ../output/model-eval
```

runner는 retrieval Hit@K/MRR, v1 사실 검사, 중복 없는 raw `usedSources` exact
검사, 최종 citation 매핑, 단계별 실패 건수와 stage/E2E p50·p95(n)를 출력한다.
산출물에는 raw 답변, prompt, 웹 본문, endpoint·credential, DB 접속정보를 저장하지
않는다. 이 구성 작업은 위 명령이나 테스트, DB·embedding·웹·GMS 실행을 허가하지
않는다.
### Hardened execution and scoring contract

- The CLI accepts only the repository-pinned dataset and policy paths. The
  policy must match the frozen dataset file, apartment target, report titles,
  20-case category split, metric list, and `hitK <= topK`. Both dataset and
  policy SHA-256 values plus policy seed are recorded in `summary.json`.
- `--env-file` is mandatory. Before it is read, every DB/embedder/web key is
  set to a controlled empty/default value; only those named keys are copied
  from that file. Ambient variables and `ai/.env*` therefore cannot redirect
  the dependency targets. GMS/model variables are ignored.
- The scripted provider accepts a call only when the system prompt is the
  production prompt and the complete user prompt exactly reconstructs the
  active question and numbered report/web blocks from runtime state.
- Raw pgvector hits and prompt-visible hits are separate. Hit@K/MRR use only
  `raw_hits[:hitK]`; one-based `usedSources` and final citation checks use the
  post-truncation prompt hits. Web host selection accepts only the exact host
  or its subdomains, not string-containing lookalikes.
- Each sample has a generation token, so a late web task from a failed prior
  sample cannot write state or timing into the next sample. A web case with an
  empty live result is reported as `web_availability`.
- Fact, exact raw-source, citation, Hit@K, and MRR summary metrics expose
  `numerator`, `denominator`, and `rate`. Technical failures remain failures in
  the fact denominator. Latency metrics continue to expose `n`, p50, and p95.
- PostgreSQL connections set both read-only mode and statement timeout. Vector
  adapter registration failure closes the connection; preflight also rejects
  non-finite sentinel embeddings and blank completion values. Only static,
  secret-free preflight details are printed.
- The E2E module contains its own v1-compatible NFKC fact/forbidden and exact
  duplicate-free source-set scorer. It does not import the GMS-oriented v1
  benchmark transitively.
