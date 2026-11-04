# PROGRESS.md

알꾼이 백엔드의 진행 상황이다. **작업 PR 안에서 갱신하고, 머지되면 확정된다.** 머지 후 문서만 고치는 변경을 만들지 않는다.

- PR을 올리기 전: 작업 커밋에 결정 이유, 다음 할 일 정리 등 이미 아는 내용을 넣는다.
- PR을 올린 뒤 머지 전: PR 번호는 PR 생성 후 첫 커밋(보통 `/code-review` 반영 커밋)에 함께 넣는다. 반영할 게 없을 때만 번호 커밋을 따로 한다.
- CI 결과는 적지 않는다. `build`가 필수 체크라 머지됐다는 것 자체가 통과를 뜻한다. 실패를 고친 경우처럼 남길 이야기가 있을 때만 적는다.


- 지금 상태: 이번 주차 목차. 체크 안 된 첫 항목이 다음 작업이다. 해당 PR에서 체크한다.
- 다음 할 일: 항목별로 정할 것과 검증 기준. 끝난 항목은 지우고 "완료"에 날짜와 함께 옮긴다.
- 작업 로그: 결정과 그 이유. 최근 날짜가 위에 온다.

## 지금 상태

### 2주차 체크리스트 — 함량 파서, 수집 배치

- [x] 성분 함량 규칙 파서 + 골든 테스트 (PR #8)
- [x] 검수 대상 판단: 못 읽은 줄, 함량 이상치 (PR #9)
- [x] V2 마이그레이션: `Product`, `Ingredient`, `ProductIngredient`, `IngestionCursor` (PR #10)
- [x] 식약처 API 클라이언트 (`RESULT.CODE` 해석, `INFO-320`이면 재시도 없이 중단) (PR #11)
- [x] 초기 적재: 페이징 → 원본 저장 → 파싱 → 멱등 upsert, 건수 확인 (PR #12)
- [x] `CHNG_DT` 증분 수집: 한 번 실행 후 종료, 건수 확인, 실행 잠금 (PR #13)
- [x] 회수 수집 (I0490 스냅샷 비교, 빠진 건은 상태만 변경) (PR #14)
- [ ] 배포 준비: 도메인 확보, SES 샌드박스 해제·소셜 로그인 검수 기간 확인

## 다음 할 일

1. 수집 배치 공통
   - `ProductIngredient.ingredientId`는 지금 `protected set`. 정규화 단계에서 `linkIngredient(id)` 같은 메서드로 연다
   - Spring Batch는 쓰지 않는다. 배치 작업은 `alkkuni.job=...`으로 켜는 `ApplicationRunner`(한 번 실행 후 종료)로 만들고, 매일 실행은 EC2 cron이 맡는다. 재시작·청크가 필요해지면 Spring Batch 도입
   - 실행 잠금은 `JobLock`(advisory lock, 출처별 키). 새 배치 작업도 `jobLock.withLock(출처)`로 감싼다
   - 날짜는 주입한 `Clock`(Asia/Seoul)으로 계산한다. `LocalDate.now()`를 직접 쓰지 않는다
   - (배포 때) `alkkuni.job` 값에 오타가 있으면 아무 작업도 안 하고 정상 종료한다. cron 명령을 만들 때 "성공 로그가 없으면 알람"으로 잡는다
   - 운영 원본 저장은 S3. 지금 `RawStore`는 로컬 파일 시스템만(3주차 배포 때)
   - 판정 규칙을 바꾸면 `IngredientAmountParser.VERSION`을 올리고 초기 적재를 다시 돌린다. upsert는 원본 해시와 `product.parser_version`이 모두 같을 때만 건너뛰므로 다시 적재가 곧 재판정이다(`REVIEWED`는 `applyParse`가 지킨다). 운영 첫 적재 전까지는 버전을 올리지 않아도 된다
   - 파서: 최종제품 표기 변형 때문에 성분명이 `제품성`, `… (최종 제품)`으로 들어간 행이 있다. `finalProduct` 표시 제거가 `최종제품`만 지운다
   - 회수 매칭(4주차): `recall`에 `product_id`·`match_status`를 추가한다. 실제 목록 399건 중 건강기능식품은 30건(유형 빈 값 39건 포함 여부는 매칭 때 확인), 신고번호가 있는 건 205건
   - 네트워크 오류 재시도는 넣지 않았다. 매일 스케줄이 다음 날 다시 받는다. 초기 적재처럼 길게 도는 작업에서 문제가 되면 추가
   - API 목록에서 사라진 제품 3건이 `parser_version` 1로 남았다(2026-11-04 기준)(삭제 대사는 전체 대사 과제)
2. 1일 기준 환산: 섭취방법 파싱과 분모 확인(`basis_check`)은 끝남. 남은 것은 1일 함량 값 계산·저장
   - `DAILY`면 함량 그대로, `PER_SERVING`이면 함량 × 1일 횟수. `UNVERIFIED`(추정, 원료 제외 33,233건)는 분모를 1일량으로 가정
   - 정할 것: `UNVERIFIED`를 화면에서 "1일 기준"으로 보일지 "표시 기준량당"으로 보일지. 비교 페이지 인상이 크게 달라진다(비교 페이지 때)
   - 분모의 정 수(`2400mg(2정)`, 333건)는 저장하지 않았다. 추정 제품을 줄일 필요가 생기면 추가
   - 분모가 여러 개인 제품은 확인하지 않는다(`UNVERIFIED`). 성분별로 확인하려면 판정을 `product_ingredient`로 옮긴다
   - 단위 환산도 이 단계: `억`·`X 10^n` 배수, `㎍/μg` 통일. `cells`(사균)와 `CFU`는 섞지 않는다
3. api 공통 응답 형식: 모든 응답을 `ApiResponse`로 감싼다(성공 `data`, 에러 `code`·`message`·`details`). 첫 컨트롤러 PR에서 `@RestControllerAdvice`와 함께 만든다
   - 정할 것: Spring 내장 에러(검증 실패, 404 등)도 같은 형식으로 바꿀 범위, 에러 코드 이름 규칙
4. 배포 준비: 도메인 확보, SES 샌드박스 해제·소셜 로그인 검수에 걸리는 기간 확인
5. (3주차 첫 작업) ECR 푸시 → EC2 기본 배포. 배포가 처음 필요한 시점이 3주차 CloudFront라 미룸
   - 정할 것: Dockerfile 방식(Boot `bootBuildImage` vs 직접 작성), OIDC IAM 역할 범위
   - 검증: EC2에서 `/actuator/health` UP
   - 확인: 운영 compose(또는 Dockerfile)에 `SPRING_PROFILES_ACTIVE=prod` 지정. 공통 yaml의 기본 프로필이 `local`이라 빠지면 `localhost` DB로 접속을 시도한다
   - 확인: 운영 DB 비밀번호는 새로 생성해 Parameter Store에 둔다. `.env.example` 값은 로컬 전용 예시
   - 워크플로: `ci.yml`과 분리한 `deploy.yml`(main 푸시). `id-token: write`는 배포 job에만
   - 이미지: t4g(ARM)라 `linux/arm64`로 빌드. QEMU보다 `ubuntu-24.04-arm` 러너 네이티브 빌드 우선 검토
   - 태그: `latest`와 `github.sha`를 함께 붙여 롤백할 이미지를 남긴다. 레이어 캐시는 `cache-from/to: type=gha`
   - 배포 job은 `concurrency`의 `cancel-in-progress: false`(중간에 끊기면 서버가 어중간한 상태로 남음)
   - 배포 후 `/actuator/health` UP 확인 단계로 끝낸다
   - 기동 순서: api(Flyway) → 헬스 UP → batch. batch는 Flyway 없이 `ddl-auto=validate`만 하므로 새 마이그레이션이 적용되기 전에 뜨면 기동에 실패한다
   - jar artifact 전달은 Dockerfile 방식이 정해진 뒤 판단. 멀티 모듈이라 경로는 `api/build/libs`, `-plain.jar` 제외

## 완료

- [x] 섭취방법 파싱과 분모 확인(`IntakeMethodParser`, V5 `basis_check`), I0030 건수 검사 완화(덜 받은 경우만 실패), 로컬에서 실제 API로 46,184건 재판정 확인 (PR #15, 2026-11-04)
- [x] I0490 회수 수집(`RecallCollectJob`, `RecallSnapshotApplier`, V4 `recall`), 로컬에서 실제 API로 399건 수집 확인 (PR #14, 2026-11-03)
- [x] I0030 `CHNG_DT` 증분 수집(`IncrementalCollectJob`, 초기 적재 끝에 커서 저장, `JobLock`), 로컬에서 실제 API로 실행 확인 (PR #13, 2026-11-03)
- [x] I0030 초기 적재(`InitialLoadJob`, `ProductUpserter`, 로컬 `RawStore`), 로컬 DB에 46,154건 적재 확인 (PR #12, 2026-11-02)
- [x] 식약처 API 클라이언트(batch `FoodSafetyClient`, `RestClient`), 실제 API 확인 (PR #11, 2026-11-02)
- [x] V2 마이그레이션(`product`, `ingredient`, `product_ingredient`, `ingestion_cursor`)과 core 엔티티 4개·enum 3개, api·batch `ddl-auto: validate` (PR #10, 2026-11-02)
- [x] 성분 함량 규칙 파서(`core`의 `IngredientAmountParser`)와 골든 테스트, 규칙 대상 194개 중 191개 정답(남은 3개는 원문 표기 오류) (2026-10-30)
- [x] 1주차 체크리스트: 멀티 모듈(PR #1), 로컬 DB·yaml 분리(PR #2), Flyway·Testcontainers(PR #5), CI(PR #6), CodeRabbit(PR #7)
- [x] CodeRabbit 연결(`.coderabbit.yaml`, 수동 리뷰), `@claude` 연동은 제외 (2026-10-29)
- [x] GitHub Actions CI(`ci.yml`), 브랜치 보호에 `build` 체크·대화 해결 필수 (2026-10-29)
- [x] Flyway `V1__enable_pg_trgm.sql`, Testcontainers 컨텍스트 테스트 3개 (2026-10-29)
- [x] 로컬 PostgreSQL `compose.yaml`·`.env.example`, api·batch yaml 공통/local/prod 분리 (2026-10-28)
- [x] 리뷰 문서 반영: `REVIEW.md`, PR 템플릿, `CLAUDE.md`·`PLANNING.md` 갱신 (2026-10-28)
- [x] `core` / `api` / `batch` 멀티 모듈 전환, Java toolchain 17 → 21, PR #1 머지 (2026-10-28)
- [x] `main` 브랜치 보호: PR 필수(승인 0명), 관리자에게도 적용, 강제 푸시·삭제 금지 (2026-10-28)
- [x] 서비스 주제 확정과 경쟁 서비스 조사 (2026-10-26)
- [x] 노션 설계 문서 v0.1, ADR-001~009 작성 (2026-10-26)
- [x] 데이터 스파이크 완료 (2026-10-27): 함량 추출률 80.8%, 정확도 78.5%, CHNG_DT 동작 확인, 회수 데이터 성격 확인, 상업 이용 조건 확인
- [x] 카카오 디벨로퍼스: 앱 생성("알꾼이"), 개인 개발자 비즈 앱 전환, 로그인 ON, 이메일 필수·닉네임 선택 동의, 로컬 리다이렉트 URI (2026-10-27)
- [x] 구글 OAuth: 프로젝트 `alkkuni`, 외부·테스트 중, 테스트 사용자 1명, 범위 `openid`·`email`·`profile`, 로컬 리디렉션 URI (2026-10-27)
- [x] 네이버 로그인: 앱 등록, 이메일 필수·별명, 로컬 Callback URL (2026-10-27)

## 미해결 질문

- [ ] 2GB 메모리로 컨테이너 4개 운영이 가능한가 (구현 초기 실측)
- [ ] 소셜 계정 연결 정책 (같은 이메일 자동 병합 여부, MVP는 병합 안 함)
- [ ] 인앱 브라우저 소셜 로그인 대응 방식
- [ ] LLM 정규화의 사용 범위와 월 비용 상한
- [ ] CHNG_DT로 삭제된 품목이 어떻게 보이는지 (전체 대사로 대응)
- [ ] 쿠팡 파트너스 최종 승인 기간 제한
- [ ] 서비스 이름 표기 통일: 노션 문서의 "알꾼" → "알꾼이"로 바꿀지
- [ ] 상표(KIPRIS)와 도메인 확보

## 작업 로그

### 2026-11-04
- 섭취방법 파싱과 분모 확인 (`feat/intake-parser`, PR #15).
  - 기준규격 `표시량(500mg/2,400mg)`의 분모는 제품 중량이고, 대체로 1일 섭취량이지만 보장되지 않는다. 섭취방법(`NTK_MTHD`)으로 "1일 횟수 × 1회 중량 = 분모"인지 확인한다. 분모의 96%가 중량(mg·g)이라 정 수만으로는 이을 수 없어, 처음 "정할 것"이던 분모의 정 수 저장(333건)은 하지 않았다.
  - `IntakeMethodParser`(core): `1일 2회, 1회 2캡슐(1,200mg)` → 횟수·개수·단위·1회 중량. 범위(`1~2정`)·선택(`또는 1일 3회`)은 읽지 않는다. 원료 제외 43,499건 중 41,761건(96.0%) 읽음, 1회 중량까지 8,119건.
  - 단위: 처음엔 1회 중량을 mg로 통일하려 했는데, 액상(ml)은 밀도를 몰라 mg로 바꿀 수 없다는 사용자 지적으로 무게는 mg, 부피는 ml로 통일하고 같은 종류끼리만 비교하게 바꿨다. 섞인 경우는 70건이라 `UNVERIFIED`로 둔다.
  - `Product.basis_check`(V5): `DAILY` 7,268 / `PER_SERVING` 497 / `MISMATCH` 117(대부분 `4mg/g`처럼 g당 표기) / `UNVERIFIED` 33,233 / `UNREADABLE` 1,649(원료 제외, 함량 있는 제품, 리뷰 반영 후 재적재 기준). 허용 오차 2%(표기 반올림).
  - 판정은 `applyParse` 안에서 하고, 검수(`REVIEWED`)로 기준규격 판정을 건너뛰어도 섭취방법은 매번 파싱한다. 섭취방법 규칙도 `IngredientAmountParser.VERSION`으로 묶어 2로 올렸다(재판정이 버전 하나로 움직이게).
  - 실제 실행: 초기 적재를 다시 돌려 46,182건 재판정. 두 번 모두 "받은 수 > `total_count`"로 실패로 끝났지만 페이지마다 커밋돼 반영됨. 원인과 수정은 아래 "I0030 건수 검사 완화".
  - 테스트: `IntakeMethodParserTest` 10개(실제 원문 변형), `ProductTest` 8개 추가(앞의 예시: 검증됨·1회분·불일치·액상·섞임·못 읽음·검수 제품). 허용 오차를 0으로 바꾸면 실패하는 것 확인.
- I0030 건수 검사 완화 (PR #15, 위 재적재 중 발견).
  - 초기 적재가 두 번 연속 실패했다. 47페이지 모두 `total_count` 46,179였는데 받은 신고번호(중복 없음)는 46,181 → 46,182건. 두 실행 사이에 늘어난 1건은 당일(11-04) 신고 제품이었다. 당일 신고 제품이 행에는 바로 나오고 `total_count`는 늦게 따라오는 것으로 보인다(추정). 이런 날엔 다시 실행해도 계속 실패하고, 증분은 커서를 옮기지 못한다.
  - 초기 적재·증분 모두 "받은 수 < `total_count`"(빠짐)만 실패로 두고, 더 많으면 경고 로그만 남긴다. 더 받은 행은 전부 저장해서 잃는 데이터가 없다. 실패해도 페이지마다 커밋된 데이터는 남고, 커서만 옮기지 않는다. 회수 수집은 그대로 둔다(덜 받은 목록으로 빠짐 처리하면 잘못된 알림이 나가서 엄격하게 유지).
  - 알려진 한계: 새 행과 빠진 행이 함께 생기면 빠짐이 가려진다. `forEachPage`는 `total_count`까지만 페이지를 요청하므로 늘어난 행이 다음 페이지로 넘어가면 이번 실행에서 받지 못한다. 둘 다 다음 증분(커서 - 1일)이나 전체 대사가 받는다.
  - 리뷰 반영 후 다시 적재(로컬 판정 버전을 1로 되돌려 전체 재판정): 받은 46,184건 > `total_count` 46,179건으로 경고만 남기고 정상 종료, 커서 11-04로 이동.
  - 테스트: `InitialLoadJobTest` 2개(더 많음 → 통과·커서 저장, 더 적음 → 실패), `IncrementalCollectJobTest` 1개(더 많음 → 커서 이동). 수정을 되돌리면 새 테스트 2개가 실패하는 것 확인.
- `/code-review`(high) 10건 중 6건 반영:
  - 곱셈 표기 `1회 3g×2포`에 중량 괄호가 없으면 1회 중량을 3g으로 읽었다(실제 6g). 분모 6g 제품이 `PER_SERVING`이어야 하는데 `DAILY`로 판정돼 1일 함량이 절반이 된다. → 1회 2포, 1회 중량 3g × 2로 읽음.
  - 검수(`REVIEWED`) 제품은 `applyParse`가 일찍 끝나 판정 버전이 옛 값으로 남아, upsert 건너뛰기 조건을 영영 못 맞춰 적재 때마다 다시 처리된다. → 섭취방법은 새 규칙으로 판정했으니 버전을 올린다. 지금 로컬에 검수 제품은 0건.
  - 분모 비교가 `BigDecimal.equals`(scale까지 비교)라 `/2,400mg`과 `/2.4g`이 다른 분모로 잡혀 `UNVERIFIED`가 됐다(로컬 96건). → scale을 지우고 비교.
  - `forEachPage`가 `total_count`까지만 페이지를 요청해, 늘어난 행이 다음 페이지로 넘어가면 받지 않았다(`total_count`가 1000의 배수면 경고도 없이 빠짐). → 마지막 페이지가 꽉 차 있으면 한 페이지 더 받는다.
  - 주석 정정: 증분 클래스 문서("다르면 실패"), 가려진 빠짐이 "다음 증분이 받는다"는 틀린 설명(커서가 옮겨져 받지 않음, 전체 대사가 필요), 테스트 주석 위치.
  - 넘긴 4건: 검수 제품의 `basis_check`가 사람이 고친 성분 행이 아니라 자동 파싱으로 계산됨(성분 행을 고치는 흐름이 아직 없음, 관리자 도구 때), 섭취방법 규칙만 바꿔도 성분 파서 버전이 올라 성분 행을 전부 다시 씀(운영 첫 적재 전이라 피해 없음, `ingredient_id`를 채우는 정규화 때 판정 버전 분리 검토), 배포 직후 `basis_check`가 NULL(운영 첫 적재가 채움), 두 파서의 숫자 정규식 중복(Nit).

### 2026-11-03
- 회수 수집 (`feat/recall-collect`).
  - `alkkuni.job=recall`, 잠금 키 `I0490`. 목록 전체(약 400건)를 받아 DB와 비교한다. 처음 보는 건 저장, 있던 건은 값 덮어쓰기, 사라진 건은 지우지 않고 `DELISTED`, 빠졌다 돌아온 건은 다시 `LISTED`. 반영은 한 트랜잭션.
  - "목록에 없으면 빠짐"이라 덜 받은 목록을 반영하면 멀쩡한 회수가 빠짐이 된다. 받은 일련번호 수가 `total_count`와 다르거나 `LISTED`의 절반 넘게 한 번에 빠지면(빈 응답 포함), 새 건 저장·갱신은 하고 빠짐 처리만 건너뛴 뒤 실패로 끝낸다. 전부 멈추면 덜 받은 목록 안의 새 회수도 저장되지 않아 알림이 늦어지기 때문이다. 제품명이 빈 행은 그 행만 건너뛴다(건수가 모자라 빠짐 처리도 건너뜀). 기준 50%는 하루 회수 변동(수 건)보다 훨씬 커서 정상 실행에서는 걸리지 않는다.
  - 커서는 쓰지 않는다. 매번 전체를 받으므로 이어 받을 기준이 없다. 수집 이력은 관리자 도구의 `collect_request`(5~6주차)가 맡는다.
  - 저장 범위: 건강기능식품만 거르지 않고 전체를 저장한다(399건 중 30건). 유형이 빈 행이 39건이라 거르면 놓칠 수 있고, 거르는 건 매칭 단계에서 한다. `product_id`·`match_status`는 매칭(4주차) 때 추가하고, 알림(Outbox)도 그때 붙인다.
  - I0490 `total_count` 391 vs 행 390 문제는 오늘 다시 받았을 때 399 = 399로 재현되지 않았다. 다시 생기면 건수 검사에서 실패로 드러난다.
  - 리팩터링: 세 수집 작업에 똑같이 있던 "페이지마다 `total_count`가 첫 페이지와 같은지" 검사를 `FoodSafetyClient.forEachPage`로 옮겼다(PR #13에서 세 번째 작업 때 보기로 한 것). `forEachPage`가 페이지 번호를 넘기고 `total_count`를 돌려준다.
  - 실제 실행: 로컬 DB에 V4 적용 후 실행, 399건 받음·새 회수 399건. 바로 다시 실행하니 새 회수 0건, 빠짐 0건(멱등).
  - 테스트: `RecallCollectJobTest` 4개(새 건·빠짐·다시 올라옴, 빈 목록, 절반 넘게 빠짐, 건수 불일치). DB 없이 저장소 mock.
- `CHNG_DT` 증분 수집 (`feat/incremental-collect`, PR #13).
  - 조회 기준일은 `커서 - 1일`. 커서가 오늘보다 뒤면 API를 부르기 전에 실패하므로 조회일은 항상 어제 이전이다(오늘 날짜는 19시 전 `INFO-700`). 겹친 하루는 수집 도중·직후에 바뀐 행을 다시 받고, 겹친 행은 upsert가 해시·판정 버전으로 건너뛴다.
  - 초기 적재와 같은 `total_count` 검사(페이지마다 같은지, 받은 신고번호 수와 같은지)를 한다. 다르면 커서를 옮기지 않고 실패로 끝내 다음 실행이 같은 구간부터 다시 받는다. 처음엔 "하루 겹침이 놓친 행을 다시 받는다"며 검사를 뺐는데, `/code-review`에서 틀렸다고 지적받음(아래).
  - 실행 방식: 처음 구현은 `@Scheduled`(앱 상주)였는데 ADR-009("필요할 때만 뜨는 컨테이너")와 `PLANNING.md`의 "스케줄·수동 실행 모두 지원"에 어긋나서 `ApplicationRunner`로 바꿨다. 상주하면 메모리 200~300MB(2GB 중)를 늘 쓰고, 수동 실행 경로가 따로 필요하다. 커서가 없으면 예외로 끝내 종료 코드로 cron이 실패를 알게 했다. `matchIfMissing`은 빼서 옵션 없이 띄운 앱에서 수집이 돌지 않는다.
  - 관리자 버튼 수집은 사용자 제안. API가 batch를 직접 띄우려면 Docker 소켓이나 AWS 권한이 필요해서, DB 요청 테이블에 쓰고 1분 cron이 실행하는 방식으로 정했다(`PLANNING.md` "관리자 운영 도구"). 구현은 5~6주차.
  - 실제 실행: 로컬 DB 커서가 없어(초기 적재가 커서 저장 커밋 전에 돌았음) `I0030`, `2026-11-02`로 SQL로 넣고 실행. `CHNG_DT=2026-11-01`, 155건 받음, 새로 쓰거나 바뀐 제품 88건(제품 46,154 → 46,178), 커서 11-03으로 이동, 원본 `changed_001.json` 저장. 바로 다시 실행하니 155건 받고 0건 변경(멱등).
  - 테스트: `IncrementalCollectJobTest` 6개(한국 날짜 기준 조회·커서 이동, 건수 불일치, total_count 변경, 미래 커서, 커서 없음, 커서 역행 금지), `JobLockTest` 2개(Testcontainers: 같은 키 거부·다른 키 허용, 실패해도 잠금 해제).
  - `/code-review`(high) 반영:
    - 하루 겹침으로는 경계에서 빠진 행이 다 돌아오지 않는다. 조회 시작일(D-2) 날짜의 행을 놓치면 다음 실행(D-1부터)이 받지 않는다. → 건수 검사 추가, 주석·`FoodSafetyClient` 설명 정정.
    - 실행 잠금 없음(`REVIEW.md` Important). 초기 적재와 증분이 겹치면 같은 신고번호 INSERT로 UNIQUE 위반, 초기 적재 끝의 커서 역행 예외. → `JobLock`(`pg_try_advisory_lock`, 세션 잠금, 연결 하나를 작업 끝까지 붙잡음)을 두 작업에 같은 키 `I0030`으로. 처음엔 관리자 수동 실행 때 넣으려 했으나 지금도 두 작업을 동시에 띄울 수 있다.
    - 시간대: `@Scheduled`를 지우면서 `zone = "Asia/Seoul"`도 사라져 JVM 기본(컨테이너는 UTC)을 썼다. 한국 00~09시 실행이면 오늘이 하루 전으로 잡혀 커서 역행 예외. → `Clock`(Asia/Seoul) 빈 주입. 테스트 자정 걸침 문제도 고정 시계로 해결.
    - `changedSince`의 `min(…, 어제)`는 정상 상태에서 쓰이지 않는 분기라 지우고, 미래 커서를 시작 시 거르게 바꿈. `ProductUpserterTest` TRUNCATE에 `ingestion_cursor` 추가.
    - 넘김: `alkkuni.job` 오타 시 정상 종료(기동 검사를 넣으면 `alkkuni.job` 없이 뜨는 `@SpringBootTest`가 깨짐, 배포 때 알람으로), 두 잡의 페이지 루프 중복(4줄, 세 번째 수집 작업 때 다시 봄).
  - 반영 후 실제 실행: `TZ=UTC`로 띄워도 커서 날짜가 한국 기준(11-03)으로 저장됨. psql 세션이 `I0030` 잠금을 잡은 동안 실행하면 "이미 실행 중"으로 실패함.
- 계획 추가: 관리자 운영 도구(파싱·회수 검수, 수집 현황·수동 실행, `ADMIN` 권한, 감사 로그)와 보안 운영(CloudFront 뒤 실제 IP 복원, 보안 그룹으로 직접 접속 차단, 호출 제한 등)을 `PLANNING.md`에 넣고 5~6주차로 잡았다. 검수 대기 데이터(`NEEDS_REVIEW` 300, `NO_AMOUNT` 1,062)가 실제로 쌓여 DB 직접 수정 대신 화면과 이력으로 처리하려는 것.

### 2026-11-02
- 초기 적재 (`feat/initial-load`, PR #12).
  - 방식을 파일 방식에서 페이징으로 바꿈. 시간당 100회(식품안전나라 안내상 낮에는 300회) 한도에서 전체가 47회라 한 번에 받는다. 파일 방식은 CSV 읽기·파일 받기·행 변환이 새로 필요해서 미룸. 페이징 경계에서 행이 빠지는 문제는 받은 신고번호 수를 `total_count`와 비교해 실패로 끝내고, 멱등이라 다시 돌린다. "목록에 없으면 삭제"로 판단하는 전체 대사는 경계 누락을 삭제로 오판하므로 파일 방식으로 남김(`PLANNING.md`).
  - 원본 저장: `RawStore`가 페이지 응답 본문을 `data/raw/{service}/{수집 시각}/page_NNN.json`에 그대로 남긴다(`.gitignore`). 가공 전에 먼저 저장. 운영 S3는 배포 때.
  - 판정은 `Product.applyParse`: 못 읽은 줄 또는 이상치 → `NEEDS_REVIEW`(사유를 `parse_issue`에), 함량 0건 → `NO_AMOUNT`. `REVIEWED`는 원문이 같으면 그대로 두고, 원문이 바뀌면 옛 원문 기준 검수라 다시 판정한다. 기준규격은 이 메서드로만 바뀐다.
  - upsert: 페이지(1000건) 단위 트랜잭션, 신고번호로 기존 제품을 한 번에 읽는다. `source_hash`(적재에 쓰는 값만 SHA-256)가 같으면 건너뛴다. 성분 행은 제품 단위로 JPQL delete 한 번 + 재삽입.
  - `product.parser_version`(V3) 추가: 판정 버전이 성분 행에만 있으면 함량 0건 제품(1,062개, 원료성 제품 대부분)은 재판정 대상으로 찾을 수 없다. 운영 데이터가 생기기 전이라 지금 넣음. 함량 행의 `parser_version`은 그대로 둔다.
  - `raw_material`: 원료성 기준만 있고 최종제품 기준이 없는 제품(로컬 적재 305건). 최종제품은 `최종 제품`(띄어쓰기)·`제품성`으로도 적어서 함께 인식한다(처음 규칙은 이 39건을 원료로 잘못 판정). "(원료성)"이 중금속 기준에만 붙은 29건은 실제 원료가 섞여 있어 원료로 둔다. 이 값을 검색·비교에서 어떻게 쓸지는 검색 기능 때 정한다. 파서 버전은 `IngredientAmountParser.VERSION = "1"`.
  - `/code-review` 9건 중 4건 반영: 페이지마다 `total_count`가 첫 페이지와 같은지 확인(받은 범위에서 삭제되고 개수가 함께 줄면 끝의 개수 비교만으로는 못 잡고, 빠진 제품은 수정일이 옛날이라 증분으로도 안 들어온다), `raw_material` 판정을 `applyParse` 안으로(밖에 두면 재판정 때 원료 여부만 옛 값으로 남고 버전은 새 값이 돼 다시 찾을 수 없다), 건너뛰는 조건에 판정 버전 추가(재판정 작업을 따로 만들 필요가 없어짐), 페이지 안 신고번호 중복 제거. 넘긴 5건: 행 하나 오류에 전체 중단(빈 제품명·날짜 오류 0건, 사람이 지켜보는 작업이라 멈추는 게 낫다), 해시에 수정일 포함(주석만 고침), 기준규격 trim(전체 1건, 원문은 `RawStore`에 있음), INSERT 배치(전체 81초), `제품성상` 오탐(0건).
  - 실행: `./gradlew :batch:bootRun --args='--alkkuni.job=initial-load'`. 새 DB는 api를 먼저 띄워 Flyway를 적용해야 한다.
  - 테스트: `ProductTest` 6개(판정 규칙, DB 없음), `ProductUpserterTest` 3개(Testcontainers, 두 번 넣어도 행 수 같음·원문 변경 시 성분 교체·검수 보존). 삭제를 빼면 실패하는 것 확인. batch는 Flyway가 없어 테스트에서만 api 마이그레이션 파일로 스키마를 만든다.
  - 실제 적재: 46,154건, 성분 행 160,155개. `NEEDS_REVIEW` 300(PR #9 결과 299와 같은 수준), `NO_AMOUNT` 1,062, `PARSED` 44,792. 전체 81초.
  - 첫 실행이 42페이지 뒤 15분 멈췄다. 원인은 API가 아니라 맥 유휴 잠자기(`pmset -g log`: 18:54:06 잠듦 → 19:09:35 깨어남). Spring의 `read-timeout`은 요청 하나의 전체 시간 타이머(`JdkClientHttpRequest$TimeoutHandler`)인데 JVM 단조 시계라 잠든 동안 흐르지 않아 안 끊긴 게 맞다. 깨어난 21초 동안 43~46페이지를 받고 다시 잠들어 47페이지 중 중단됐다. 다시 돌리니 바뀐 제품 154건(남은 1페이지)만 썼다. 실제 데이터로 멱등 확인. 로컬에서 길게 돌릴 때는 `caffeinate -i`로 실행한다. EC2는 잠들지 않아 운영과는 무관.
- 식약처 API 클라이언트 (`feat/foodsafety-client`, PR #11). CI `build` 통과(1분 1초), CodeRabbit은 수동 요청이라 건너뜀. batch에 `spring-boot-starter-restclient`만 추가(웹 서버 없음).
  - `fetchPage`가 `RESULT.CODE`를 해석: `INFO-000` 페이지, `INFO-200` 빈 페이지, `INFO-320` `FoodSafetyRateLimitException`(재시도 없음), 나머지 `FoodSafetyApiException`. `forEachPage`가 1000건씩 끝까지 받는다. 페이지에 응답 본문(`rawBody`)을 담아 원본 저장에 그대로 쓴다.
  - 인증키가 URL 경로에 들어가 네트워크 오류 메시지에 섞인다. 메시지에서 키를 가리고 원인 예외는 붙이지 않는다. 마스킹을 빼면 테스트가 실패하는 것 확인.
  - 키는 `foodsafety.api-key: ${FOODSAFETY_API_KEY}`. 로컬 `.env`, 운영 Parameter Store. 비어 있으면 기동 실패.
  - 테스트: `MockRestServiceServer` 7개(정상 응답은 스파이크 실제 응답을 2행으로 자름). 실제 호출 `FoodSafetyClientLiveTest` 3개는 `FOODSAFETY_LIVE_TEST=true`일 때만.
  - `/code-review` 8건 중 6건 반영: 오류 메시지에서 키가 든 URL 제거(키를 찾아 가리던 방식은 `+/=`가 인코딩되면 놓침), JSON 아닌 응답·`total_count` 누락·배열 아닌 `row`를 API 예외로 통일, `FoodSafetyPage` `toString`에서 본문 제외, 안 쓰는 `jackson-module-kotlin` 제거. 넘긴 2건: 위치 기반 페이징 누락(전체 적재는 파일 방식이라 해당 없음, 주석만 수정), `INFO-700` 전용 처리(증분에서 조회 날짜를 어제로 제한).
  - api 응답 형식은 공통 객체로 감싸는 방식(A)으로 결정. `ProblemDetail`(성공은 DTO 그대로, 에러만 RFC 9457)과 비교해 사용자가 익숙하고 성공·에러 모양이 하나인 쪽을 골랐다.
  - 실제 확인: I0030 1000건 한 번에 4.2초(전체 46,119건), CHNG_DT 최근 7일 327건, I0490 391건. `INFO-200`은 서비스 객체 안에 `RESULT`가 온다(스파이크 코드는 최상위도 봤음). `INFO-320` 본문은 못 봐서 두 위치 모두 읽는다. 오늘 날짜 CHNG_DT는 19시 전 `INFO-700`.
- V2 엔티티와 마이그레이션 (`feat/v2-entities`, PR #10). 엔티티는 사용자가 직접 작성하고 Claude가 리뷰했다.
  - 패키지는 기능별: `core.product`(Product, ProductIngredient, enum), `core.ingredient`, `core.ingestion`.
  - `ProductIngredient`는 `@ManyToOne` 대신 `productId: Long`, `ingredientId: Long?` 값만 둔다. 처음 가이드는 `@ManyToOne(LAZY)` 단방향이었으나 사용자 제안으로 바꿈. 배치 대량 저장 때 프록시 없이 id만 넣으면 되고, 조회는 제품 id 기준이라 객체 탐색이 필요 없다. 참조 무결성은 V2의 FK 제약이 지킨다. 정리: 노션 「ProductIngredient: @ManyToOne 대신 FK 값만 보관」
  - `ingredient_id`가 null인 경우: 정규화 전, 또는 정규화 후에도 사전에 없는 성분(억지로 붙이면 틀린 비교가 됨).
  - 함량·기준량은 정밀도 미지정 `NUMERIC`/`BigDecimal`. 엔티티 리뷰에서 `Int`로 쓴 것을 잡음(2.4μg 같은 소수 함량이 잘림). 처음엔 `NUMERIC(20,6)`이었으나 `/code-review`에서 CFU 15자리 오버플로·소수 7자리 반올림을 지적받아 정밀도를 뺐다. `raw_name`도 같은 리뷰에서 `VARCHAR(255)` → `TEXT`(구분자 없는 원문은 이름 길이 상한이 없음).
  - `/code-review` 지적 중 확신도·원문 누락은 결정(2026-10-30)과 문서가 어긋난 것이라 `REVIEW.md`·`PLANNING.md` 문구를 지금 결정에 맞춤. 성분 행 멱등은 수집 배치 PR에서, `Ingredient.defaultUnit`·`Product.status` 변경 메서드는 쓰는 곳이 생길 때 추가.
  - CI `build` 통과(1분 1초). CodeRabbit은 시간당 한도로 건너뜀.
  - `var` 필드는 모두 `protected set`. `kotlin-jpa`/`allOpen`이 프로퍼티를 open으로 만들어 `private set`은 쓸 수 없다.
  - V2는 `erd.sql`에서 `raw_text`·`confidence`·`review_status`를 빼고 `parse_status`·`parse_issue`를 더했다. ID는 `GENERATED ALWAYS AS IDENTITY`. PostgreSQL은 FK에 인덱스를 만들지 않아 `product_ingredient.product_id` 인덱스를 추가. 제품명 `pg_trgm` GIN 인덱스는 검색 기능 때 V3로.
  - api·batch에 `ddl-auto: validate`. Flyway는 api에만 있어, 새 DB나 새 마이그레이션은 api가 먼저 적용해야 batch가 뜬다. batch에도 Flyway를 넣는 방법은 지금은 필요 없어 보류.
  - 검증: Testcontainers 컨텍스트 테스트에 V2 적용 확인 추가(api 테스트 4개 통과). 컬럼명을 일부러 틀려 `Schema validation: missing column`으로 실패하는 것도 확인.

### 2026-10-31
- 검수 대상 판단 (`feat/parser-review-flags`, PR #9). V2 엔티티에서 `NEEDS_REVIEW`를 정하려면 파서가 "못 읽음"을 알려야 해서 엔티티보다 먼저 한다. 엔티티는 사용자가 직접 작성하기로 해서 다음 PR로 나눔.
  - `parse()`가 `ParseResult(amounts, unreadLines)`를 돌려준다. 못 읽은 줄: 느슨한 함량 모양(숫자에 아무 쉼표·마침표, 분모 단위 무관)이 엄격한 패턴 매치 밖에 있는 줄. 일부러 버린 값(오염물질 `mg/kg`, 이하 상한, 세균수)과 원료성 제품 줄은 넣지 않는다.
  - 처음엔 18,702개 제품이 잡혔다. `1. 납(mg/kg)`의 항목 번호를 함량으로 읽어서다. 단위 자리에 괄호·콜론·한글을 받지 않게 해 246개로 줄임. 남은 오탐은 `세균수 : 1ml/당 100`, `1㎎/㎏ 이하` 류 44줄로 검수에서 거른다.
  - `AmountSanityCheck`: `1.600 mg`은 정상 숫자로 읽혀 못 읽은 줄이 아니다. 숫자 하나로는 오기인지 알 수 없어(소수점 3자리 규칙은 정상 값 포함 999개 제품) 값끼리 모순을 본다. 함량 > 분모(질량 단위끼리), 같은 제품 분모의 1000배 차이. 단위 오기(`2.4g/200mg`, `500mg`/`500g`)도 잡힌다. 값은 고치지 않는다.
  - 결과(I0030 46,057건): 못 읽은 줄 246개 제품, 이상치 56개 제품, 합쳐 299개(0.65%). 이상치 표본은 모두 실제 오기. 골든 200건에서 검수로 가는 제품 = 알려진 실패 3개.
  - 분모가 정(`0.138mg/4정(2,000mg)`)인 줄도 못 읽은 줄로 간다. 1일 기준 환산 단계에서 정 수를 저장하면 읽을 수 있다.
  - 정리: 노션 「함량 이상치 검사: 고치지 않고 검수로 보내기」
  - `/code-review` 10건 중 4건 반영: 분모 1 필터가 `1.000mg`(1,000mg의 오기)까지 빼던 문제(필터 삭제, 오탐 증가 0), `mg α-TE`·`mg NE` 단위를 질량으로 인식, 골든 상수 `knownFailures`/`needsReview` 분리, `unreadLines`가 나눈 조각이라는 주석.
  - 넘긴 6건(전체 데이터로 확인): 농도 `100g`과 1회 분량 `100mg` 섞임 오탐(같은 숫자 g/mg 8건 모두 실제 오기), `+` 뒤 숫자(1줄, 깨진 지수 표기라 검수가 맞음), `80%/120%`(0건), 메시지에 원문 표기 누락(원문은 검수 때 함께 봄), 정규식 중복 실행(전체 몇 초), 느슨한·엄격 패턴 문자 목록 중복(받는 범위가 일부러 다름).
  - 규칙 3(분모 질량 10mg 미만이면 의심)은 노션에 정리만 하고 보류. 모든 성분이 같은 오기 분모(`0.45 mg/1.400 mg`)를 쓰면 규칙 1·2로 못 잡는 4개 제품이 대상. 경계값을 이 데이터 분포로 정해서 검수 화면에서 실제 대상을 본 뒤 판단한다.
  - CI `build` 통과(58초).

### 2026-10-30
- 성분 함량 규칙 파서 (`feat/ingredient-parser`, PR #8). 방향은 노션 「성분 함량 파싱 방식」(규칙 → 실패분만 LLM → 사람 검수) 중 규칙 단계.
  - `core`에 순수 Kotlin `object`로 둔다. 스프링·DB 없이 테스트하고, 이후 재파싱·검수에서도 쓴다.
  - 출력은 원문 표기 그대로(`raw_name, amount, unit, basis_amount, basis_unit`). `㎍/μg`, `억CFU`, `X 10^11 CFU` 통일·환산은 정규화 단계.
  - "표시량(" 글자가 아니라 "숫자 단위/숫자 단위" 모양으로 찾도록 바꿈(개선 3번). 대괄호·중괄호·`표시량의(…)`·뒤에 오는 `(표시량 : …)`이 같은 규칙으로 잡힌다(개선 2번).
  - 프로바이오틱스 `100,000,000(1억) CFU`의 괄호 억 표기는 버림(개선 1번). 분모에 단위만 있으면 1(개선 6번). 원료성 제품 값은 버리고, 성분명이 위 줄에만 있으면 위 줄 이름을 쓴다.
  - 함량이 아닌 값 제외: 분모가 `kg`(오염물질), 뒤에 "이하"(EGCG 상한, 세균수).
  - 골든 데이터: `core/src/test/resources/golden/`. 스파이크 O 157개는 스파이크 출력, 나머지 43개는 Claude 초안을 기준으로 함. 단위가 성분명 괄호에 있는 6개는 `LLM` 대상이라 채점에서 뺌(194개 채점).
  - 스파이크 O 판정 중 3개를 정정: 놓친 프로바이오틱스 1행 추가, 분모 단위 `g 20정` → `g`, 원문 `4, 100mg` 오타를 4100mg으로.
  - 사람 검토 반영: 원문 분모 오타 `1.600 mg`을 1600으로 정정(같은 제품 다른 성분 분모가 1,600 mg), 지표성분으로 표시된 함량(`가르시니아캄보지아 추출물 : 총 HCA로서 750mg`)은 지표성분 이름을 성분명으로. 지수·억 표기는 원문 그대로 두고 환산은 정규화 단계에서 한다.
  - 결과: 스파이크 이식 160/194 → 191/194. 남은 실패 3개는 원문 표기 오류(분모 `1.600 mg`, `4, 100mg`, 소수점 쉼표 `5,5mg`)라 규칙으로 맞히지 않고 검수로 보낸다(개선 7번). 골든 테스트는 맞힌 개수가 아니라 이 3개와 실패 목록이 정확히 같은지 본다. 개수만 보면 하나가 고쳐지고 다른 하나가 깨져도 통과해서다.
  - `/code-review` 지적을 전체 품목(I0030 46,057건)에 파서를 돌려 실제 건수로 확인하고 고침. 리뷰 재현 문장은 지어낸 예시라 실제 원문으로 `IngredientAmountParserTest`를 만들었다.
    - 쉼표는 세 자리 구분일 때만 숫자로 읽는다. `1,5g`, `9,6g`은 소수점인지 오타인지 알 수 없어 추측하지 않고 버린다. 전에는 10배 값(분모 96g 등)이 경고 없이 들어갔다(61개 제품이 0건으로 바뀜).
    - 단위가 뒤 내용을 삼키던 문제(`3 ㎍, 120 IU`, `1. MSM 1.5g`), 범위 위쪽 값(`80~120mg/g`), 두 번째 성분명 오염(`, 비피더스균`), 세균수, 기타원료 유래 다당체 참고값, 같은 성분을 다시 적은 괄호 표기(`(2000IU/500mg)`), 성분명 없는 최종제품 줄이 위 항목명 "성상"을 가져가던 문제.
    - 전체 결과(수정 전 → 후): 단위 오염 55 → 13개 제품, 성분명 오염 158 → 7, 성상·세균 이름 142 → 9, 기타유래 다당체 172 → 1. 남은 것은 `10^9 CFU`, `E+11` 같은 지수 표기 변형이라 정규화 단계에서 본다.
    - 함량이 범위(`823.2mg~1,029mg/6,000mg`)면 아래값을 쓴다. 보장되는 최소량이라 비교에서 함량을 부풀리지 않는다. 전에는 위쪽 값이 들어갔다(전체 2개 제품).
    - 골든 정답에서 스파이크 O 판정 2개를 정정: 소수점 쉼표 `5,5`를 55로 적은 제품(10배 값), 성분명이 `.대장균군`으로 들어간 아연.
  - 골든 데이터에 맞춰 고친 결과라 과적합 가능성이 있어 전체 데이터 추출률과 새 표본 검수로 확인한다.
  - CI `build` 통과(58초). CodeRabbit 리뷰는 요청하지 않음(사용자 결정).
  - 확신도: 점수 대신 검수 필요 사유만 기록하기로 함. 저장 위치는 V2 엔티티 작업에서 정한다.

### 2026-10-29
- CodeRabbit 연결 (`chore/coderabbit`, PR #7)
  - `@claude` GitHub 연동은 하지 않기로 함. 1인 개발이라 질문은 로컬 Claude Code로 충분하고, 연결하면 시크릿과 워크플로 파일(위험 경로)만 늘어난다. (`PLANNING.md` "리뷰 구조")
  - `.coderabbit.yaml`: 한국어 리뷰, 자동 리뷰 끔(`@coderabbitai review`로 수동 요청).
  - CodeRabbit도 로컬과 같은 `REVIEW.md`·`CLAUDE.md` 기준으로 리뷰한다. 기본 설정(`code_guidelines`)이 두 파일을 읽으므로 따로 지정하지 않는다. 기준을 빼면 넓게 보지만 이미 정한 규칙(엔티티 `data class` 금지 등)과 부딪히는 오탐 처리 비용이 더 크다고 봄. 관점 차이는 기준보다 도구·모델 차이에서 나온다.
  - 검증: `@coderabbitai review`로 리뷰 받음(Minor 2건, 문서 표현). `REVIEW.md`·`CLAUDE.md`는 기본 탐색 패턴에 들어가 설정상 적용 대상임을 CodeRabbit 답변으로 확인. 실제 실행에서 지침으로 읽었는지는 리뷰 기록에 남지 않아 확인 불가. 이후 리뷰에서 `REVIEW.md` 기준(심각도 표기, "지적하지 말 것")을 따르는지로 판단한다.
  - 리뷰 한도: 이 레포는 시간당 리뷰 1회(OSS 등급, 개발자 단위, 최근 1시간 기준으로 다시 채워짐). 채팅은 별도 한도라 질문은 리뷰를 쓰지 않는다. 지적 반영은 모아서 푸시한다.
  - `CLAUDE.md`의 `PROGRESS.md` 갱신 규칙을 "작업을 마칠 때"에서 "작업 PR 안에서"로 바꿈. 새 `PROGRESS.md` 상단 규칙(머지 후 문서만 고치는 변경 금지)과 맞춤.
- GitHub Actions CI (`chore/ci`)
  - `ci.yml` 하나만 둔다. CD는 트리거(main 푸시)와 조건이 달라 3주차에 `deploy.yml`로 분리. 한 파일에서도 job별 `permissions`로 권한은 나눌 수 있지만, 파일을 나누면 조건이 단순하고 브랜치 보호 체크 이름이 CD 작업에 흔들리지 않는다.
  - Gradle 캐시는 `actions/cache` 대신 `setup-gradle`. `gradlew`는 git에 100755로 들어가 있어 `chmod` 단계 없음. checkout에 `persist-credentials: false`.
  - 테스트는 `@ActiveProfiles("test")`로 고정. `application-test.yaml`은 넣을 값이 없어 두지 않는다. 프로필을 지정하면 `profiles.default: local`이 무시돼 `.env` import를 타지 않고, DB는 `@ServiceConnection`이 덮어쓴다.
  - 러너(`ubuntu-latest`)의 기본 Docker로 Testcontainers 통과, 1분 49초.
  - 브랜치 보호: `build` 체크(GitHub Actions 앱으로 고정)와 대화 해결 필수 추가. 브랜치 최신화 필수(`strict`)는 1인 개발이라 매번 갱신하는 비용만 생겨 끔. CodeRabbit 리뷰 완료는 필수로 걸지 않는다(장애 시 머지가 막힘).
- Flyway·Testcontainers (`chore/flyway-testcontainers`)
  - `V1`은 `pg_trgm` 확장만 넣음. 비워 두면 Flyway가 SQL을 적용하는지 테스트로 확인할 수 없고, 테이블은 ERD가 2주차 수집 결과로 바뀔 수 있어 엔티티를 만들 때 V2부터 넣는다.
  - 컨텍스트 테스트(`postgres:17`, `@ServiceConnection`): Flyway V1 적용·`pg_trgm` 존재, 한글 trigram 생성, 헬스 UP. 테스트는 기본 프로필 `local`로 돈다. 작업 디렉터리가 루트면 `.env`가 섞일 수 있어 CI 작업에서 `test` 프로필을 정한다.
  - V1에 `WITH SCHEMA public`(사용자 이름 스키마에 설치되는 것 방지)과, `show_trgm('가')`가 비면 예외를 던지는 `DO` 블록을 넣음. 테스트는 컨테이너만 보므로 운영 DB가 C 로케일이면 배포 시점에 막는다. C 로케일 컨테이너에서 실패, en_US.utf8에서 통과 확인. PG16+는 `lc_ctype` 설정이 없어 `pg_database.datctype`으로 읽는다.
  - 한글 trigram 확인: `show_trgm('비타민')`이 `0x...`로 나와 처음엔 로케일 문제로 보였으나, 컨테이너 로케일은 `en_US.utf8`로 정상. 멀티바이트 trigram은 해시로 저장될 뿐이라 개수(4개)로 검증한다. 로케일이 `C`면 빈 배열이 나온다.
- EC2 배포를 1주차에서 3주차 첫 작업으로 미룸. 배포가 처음 필요한 건 3주차 CloudFront(EC2가 원본)와 4주차 소셜 로그인 웹훅(공개 HTTPS)이고, 2주차 수집·정규화는 로컬과 S3로 충분하다. 대신 도메인 확보와 SES·소셜 로그인 검수 기간 확인을 2주차에 끝내 승인 대기가 4주차 공개를 막지 않게 한다. (`PLANNING.md` 마일스톤)
- 2주차 체크리스트는 1주차가 끝나면 자세히 쓴다. 2주차 수집·정규화 결과를 봐야 3~4주차 모양이 정해진다.

### 2026-10-28
- 리뷰 구조 확정: 로컬 Claude `/code-review` + `REVIEW.md`, PR은 CI + CodeRabbit, 위험 경로는 `/security-review`와 직접 정독, 파서는 골든 테스트. (`PLANNING.md` "리뷰 구조")
- `REVIEW.md` 작성·검토, PR 템플릿 작성, `CLAUDE.md`에 브랜치 + PR 규칙, 빌드 결과 보고, `REVIEW.md` 연결 규칙 추가.
- 멀티 모듈 전환 (`chore/multi-module`)
  - 루트 `build.gradle.kts`는 플러그인 버전(`apply false`)과 공통 설정(`subprojects`)만 둔다. buildSrc·버전 카탈로그는 아직 쓰지 않음.
  - `core`: Boot 플러그인 없는 라이브러리. `spring-boot-starter-data-jpa`를 `api`로 노출, `kotlin-jpa`·`allOpen` 설정.
  - `api`: Boot 앱(`com.alkkuni.AlkkuniApiApplication`), web·actuator·validation·Flyway. `batch`: Boot 앱 골격만 둠(Spring Batch 도입 여부는 2주차에 결정).
  - 메인 클래스는 모듈 패키지가 아니라 최상위 `com.alkkuni`에 둔다. 기본 스캔 범위가 메인 클래스 패키지 아래라서, 이렇게 해야 `scanBasePackages`·`@EntityScan`·`@EnableJpaRepositories` 없이 core까지 스캔된다.
  - Java toolchain을 생성 시 값 17에서 문서대로 21로 맞춤.
  - 생성기가 만든 `contextLoads` 테스트 삭제: DB 설정이 없어 `main`에서도 실패하던 테스트. Testcontainers 컨텍스트 테스트로 대체 예정(다음 할 일의 Flyway·Testcontainers 항목).
- `gh` CLI 설치·로그인, PR #1 생성.
- `main` 브랜치 보호 설정(PR 필수, 승인 0명, 관리자 포함). CI 체크 필수는 CI를 만든 뒤 추가.
- 머지 방식 결정: Squash 대신 일반 머지. 리뷰 반영·수정 커밋까지 `main`에 남겨 작업 과정을 본다. (`PLANNING.md` "리뷰 구조")
- 로컬 DB와 yaml 분리 (`chore/local-db`)
  - 루트 `compose.yaml`에 `db`(postgres:17, 127.0.0.1:5432만 노출, named volume, healthcheck). 계정은 `.env`의 `DB_NAME`·`DB_USERNAME`·`DB_PASSWORD`.
  - yaml: 공통에 `spring.profiles.default: local`과 `username`·`password`(`${...}`), local은 `localhost` URL, prod는 `${DB_URL}`.
  - `.env` 읽기: local 프로필에서 `spring.config.import: optional:file:.env[.properties]`, `bootRun`의 작업 디렉터리를 레포 루트로 맞춤. IntelliJ 실행 구성도 작업 디렉터리를 루트로 둬야 한다.
  - 주의: `workingDir` 변경은 `.env`뿐 아니라 앱의 모든 상대 경로에 적용된다. `bootRun`은 루트 기준, jar·테스트는 모듈 기준이 되므로 상대 경로 파일을 쓰게 되면 다시 검토한다.
  - `/code-review` 반영: `spring-boot-docker-compose`로 바꾸자는 제안은 앱 실행과 DB 관리를 분리하려고 채택하지 않음. `profiles.default: local`과 `.env.example` 예시 비밀번호는 위험이 낮아 유지하고, 운영 배포 때 확인할 항목으로 다음 할 일 4번에 옮김.
  - batch에는 Flyway 의존성이 없어 따로 끌 설정이 없다(마이그레이션은 api만).
  - 확인: `:api:bootRun` → `/actuator/health` UP(PostgreSQL 17.11 연결), `:batch:bootRun` 기동, `./gradlew build` 성공.

### 2026-10-27
- 스파이크 마무리: 200개 정확도 검수, 회수 데이터 성격(소비기한이 남은 목록) 확인, CHNG_DT 동작 테스트, 상업 이용 조건 확인. 노션 보고서에 한 줄 결론과 "한눈에 보기" 표 추가.
- 카카오·네이버·구글 로컬 개발용 OAuth 설정 완료. 웹훅·운영 URI·검수 요청은 배포 후로 미룸 (노션 "출시 준비 체크리스트").
- IntelliJ 프로젝트 설정 결정: Spring Boot 제너레이터, Kotlin, Gradle - Kotlin, 그룹·패키지 `com.alkkuni`, Java 21, Jar, YAML.
  초기 의존성: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Actuator. Security·OAuth2 Client는 로그인 단계에서 추가.
- Claude Code 협업용 `CLAUDE.md`, `docs/PLANNING.md`, `docs/PROGRESS.md` 작성.
- GitHub 레포 `alkkuni-backend` 생성, 첫 커밋 푸시.
