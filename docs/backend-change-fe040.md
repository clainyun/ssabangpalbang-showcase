# FE-040 작업 중 백엔드 변경 내역 (롤백 가이드)

프론트(FE-040 아파트 검색)를 붙이면서 **프론트 담당자가 백엔드를 직접 수정**한 내역입니다.
문제가 생기면 이 문서만 보고 되돌릴 수 있게 정리했습니다.

- 작업일: 2026-07-31
- 브랜치: `feature/fe-007-apartment-detail`
- 변경 직전 커밋: **`c3638b193b31844032ccfe0fab2bfd5c8d77861e`**
- 변경 전 `backend/` 는 **워킹트리가 완전히 깨끗한 상태**였습니다.

## 한 줄 롤백

`backend/` 변경만 전부 되돌립니다. 프론트 변경은 그대로 남습니다.

```bash
git restore backend/ docs/API.md
```

되돌린 뒤 프론트도 좌표를 안 쓰게 맞추려면 `frontend/src/features/apartment/api/searchApartments.ts`
의 `latitude`/`longitude` 를 다시 옵셔널(`latitude?: number | null`)로 바꾸면 됩니다.
그대로 둬도 값이 `undefined` 로 들어와 **마커만 안 찍힐 뿐 오류는 나지 않습니다.**

---

## 무엇을 왜 바꿨나

**아파트 검색 API(`GET /api/v1/apartments`, BE-041) 응답에 `latitude`/`longitude` 추가.**

좌표는 DB에 이미 있고 반경 계산(`ST_MakePoint(a.longitude, a.latitude)`)에도 쓰고 있었는데
응답 DTO에서만 빠져 있었습니다. 그래서 검색 결과를 목록으로만 보여줄 수 있었고 지도 마커로는
찍을 수 없었습니다.

- **기존 필드는 하나도 안 건드리고 추가만** 했습니다.
- 새 쿼리·조인 없음. 이미 조회 중인 `apartment` 테이블의 컬럼 2개를 select 에 넣은 것뿐입니다.
- 성능 영향 없음.

## 변경 파일 (운영 코드 3개)

### 1. `apartment/repository/ApartmentSearchRow.java`

```java
 public record ApartmentSearchRow(
         Long apartmentId, String name, String address,
-        String districtName, String dongName, Integer distanceMeters
+        String districtName, String dongName,
+        Double latitude, Double longitude,
+        Integer distanceMeters
 ) {
 }
```

### 2. `apartment/repository/ApartmentSearchQueryRepository.java`

**(a) QueryDSL select — KEYWORD / REGION 모드**

```java
                 .select(Projections.constructor(ApartmentSearchRow.class,
                         apartment.id, apartment.name, apartment.address,
                         apartment.districtName, apartment.dongName,
+                        apartment.latitude, apartment.longitude,
                         Expressions.nullExpression(Integer.class)))
```

**(b) 네이티브 쿼리 SELECT 절 — NEARBY 모드**

```sql
                 SELECT a.id, a.name, a.address, a.district_name, a.dong_name,
+                       a.latitude, a.longitude,
                        CAST(ROUND(ST_Distance(
```

**(c) 네이티브 쿼리 결과 매핑** — 좌표가 앞에 끼면서 distance 가 `row[5]` → `row[7]` 로 밀립니다.

```java
         List<ApartmentSearchRow> content = rows.stream().map(row -> new ApartmentSearchRow(
                 ((Number) row[0]).longValue(), (String) row[1], (String) row[2],
-                (String) row[3], (String) row[4], ((Number) row[5]).intValue()
+                (String) row[3], (String) row[4],
+                row[5] == null ? null : ((Number) row[5]).doubleValue(),
+                row[6] == null ? null : ((Number) row[6]).doubleValue(),
+                ((Number) row[7]).intValue()
         )).toList();
```

### 3. `apartment/dto/response/ApartmentSearchItem.java`

record 필드 2개 추가 + `from()` 에서 전달.

```java
         String districtName, String dongName,
+        Double latitude, Double longitude,
         ApartmentLatestTransaction latestTransaction,
```

```java
                 row.apartmentId(), row.name(), row.address(), row.districtName(), row.dongName(),
+                row.latitude(), row.longitude(),
                 latest, latest != null, row.distanceMeters(), favorited
```

## 변경 파일 (테스트 2개)

생성자 인자만 늘어났고 **검증 로직은 그대로**입니다.

| 파일 | 변경 |
|---|---|
| `ApartmentSearchServiceTest.java:67` | `new ApartmentSearchRow(..., 37.5010, 127.0396, null)` |
| `ApartmentSearchControllerTest.java:57` | `new ApartmentSearchItem(..., 37.5010, 127.0396, ...)` |

`HTTP_응답은_정본_필드만_반환한다` 가 막는 필드는 `completedReportCount`·`hasNext`·`householdCount`
뿐이라 좌표 추가로 깨지지 않습니다.

## 문서

`docs/API.md` — 아파트 검색 응답 예시 2곳에 `latitude`/`longitude` 추가.

## 검증 결과

```
./gradlew compileJava compileTestJava   → BUILD SUCCESSFUL
./gradlew test postgresTest --tests "*ApartmentSearch*"  → BUILD SUCCESSFUL
```

`postgresTest` 는 실제 PostGIS 컨테이너를 띄우는 통합 테스트라, NEARBY 네이티브 쿼리의
컬럼 인덱스가 밀린 부분(`row[7]`)까지 실제 실행으로 검증됐습니다.

---

# 시도했다가 되돌린 것 — 한글 정렬

**결론: 손대지 않았습니다.** 버그는 실재하지만 이 브랜치에서 고칠 수 있는 문제가 아닙니다.

## 증상

`ORDER BY name` 이 한글에서 가나다순이 아닙니다.

```sql
SELECT x FROM (VALUES ('역삼래미안'),('역삼럭키'),('역삼경남'),('롯데캐슬노블'),('가나다')) t(x)
ORDER BY x;

  가나다
  역삼경남
  역삼럭키       ← 래 < 럭 인데 래미안보다 앞
  역삼래미안
  롯데캐슬노블   ← 맨 뒤
```

원인은 DB 로케일입니다.

```sql
SELECT datcollate, datlocprovider FROM pg_database WHERE datname='ssabangpalbang';
-- en_US.utf8 | c
```

`en_US.utf8`(glibc)에는 한글 음절 정렬 가중치가 제대로 정의돼 있지 않습니다.

## 왜 못 고쳤나

`COLLATE "C"` 를 QueryDSL `orderBy` 에 넣어 봤지만 **HQL이 `COLLATE` 문법을 모릅니다.**

```
org.hibernate.query.SyntaxException:
  At 4:24 and token 'COLLATE', mismatched input 'COLLATE'
  [... order by apartment.name COLLATE "C" asc]
```

QueryDSL은 HQL을 생성하고 Hibernate 6 HQL 파서에 `COLLATE` 가 없어서 파싱 단계에서 막힙니다.

## 고치려면 (별도 티켓 권장)

1. **Flyway 마이그레이션으로 컬럼 콜레이션 변경** — `ALTER TABLE apartment ALTER COLUMN name TYPE varchar(n) COLLATE "C"`.
   해당 컬럼 인덱스가 재생성돼야 하므로 영향 검토 필요.
2. **Hibernate `FunctionContributor` 등록** — `collate_c(name)` 같은 커스텀 SQL 함수를 만들어 HQL에서 호출.
   운영 코드에 인프라성 클래스가 하나 늘어납니다.
3. **DB 자체를 `C.utf8` 또는 ICU `ko-KR` 로 재생성** — 가장 근본적이지만 DB 재생성 필요.

현대 한글 음절(가~힣)은 유니코드 코드포인트 순서가 곧 가나다순이라, 어느 방법이든
`C` 콜레이션이면 정확합니다.

`docs/API.md` 에 명시된 다른 한글 정렬(예: 행정구역 목록 `dongName ASC`)도 같은 영향을 받습니다.
