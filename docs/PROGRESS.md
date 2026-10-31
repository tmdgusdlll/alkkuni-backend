# PROGRESS.md

알꾼이 백엔드의 진행 상황이다. **작업 PR 안에서 갱신하고, 머지되면 확정된다.** 머지 후 문서만 고치는 변경을 만들지 않는다.

- PR을 올리기 전: 작업 커밋에 결정 이유, 다음 할 일 정리 등 이미 아는 내용을 넣는다.
- PR을 올린 뒤 머지 전: CI 결과, PR 번호처럼 PR이 있어야 아는 내용만 같은 브랜치에 커밋을 더한다.


- 지금 상태: 이번 주차 목차. 체크 안 된 첫 항목이 다음 작업이다. 해당 PR에서 체크한다.
- 다음 할 일: 항목별로 정할 것과 검증 기준. 끝난 항목은 지우고 "완료"에 날짜와 함께 옮긴다.
- 작업 로그: 결정과 그 이유. 최근 날짜가 위에 온다.

## 지금 상태

### 2주차 체크리스트 — 함량 파서, 수집 배치

- [x] 성분 함량 규칙 파서 + 골든 테스트 (PR #8)
- [x] 검수 대상 판단: 못 읽은 줄, 함량 이상치
- [ ] V2 마이그레이션: `Product`, `Ingredient`, `ProductIngredient`, `IngestionCursor`
- [ ] 식약처 API 클라이언트 (`RESULT.CODE` 해석, `INFO-320`이면 재시도 없이 중단)
- [ ] 초기 적재: 파일 방식 → 원본 저장 → 파싱 → 멱등 upsert
- [ ] `CHNG_DT` 증분 수집
- [ ] 회수 수집 (I0490 스냅샷 비교, 빠진 건은 상태만 변경)
- [ ] 배포 준비: 도메인 확보, SES 샌드박스 해제·소셜 로그인 검수 기간 확인

## 다음 할 일

1. V2 마이그레이션과 엔티티
   - 정한 것: `Product.parseStatus`(`PARSED` / `NO_AMOUNT` / `NEEDS_REVIEW` / `REVIEWED`)와 `parseIssue`(이유 문자열 하나). 함량 0건 제품은 `product_ingredient` 행이 없어 구분할 곳이 제품뿐이다. 재파싱은 `REVIEWED`를 덮어쓰지 않는다
   - 판정: `unreadLines` 또는 `AmountSanityCheck.issues()`가 있으면 `NEEDS_REVIEW`, 함량 0건이면 `NO_AMOUNT`
   - `erd.sql`과 다른 점: `product_ingredient.confidence`·`review_status` 삭제, `ingredient_alias`·`unit_conversion`은 정규화 단계로 미룸
   - api·batch yaml에 `spring.jpa.hibernate.ddl-auto: validate` 추가(지금은 기본값 `none`이라 검증이 돌지 않는다)
   - 검증: Testcontainers에서 V2 적용, `ddl-auto=validate` 통과
2. 수집 배치(API 클라이언트 → 초기 적재 → 증분 → 회수)
   - 정할 것: 로컬 원본 저장소(파일 시스템 추천, 운영은 S3)
   - Spring Batch는 쓰지 않고 `@Scheduled` + PostgreSQL advisory lock으로 시작. 재시작·청크가 필요해지면 도입
3. 1일 기준 환산: 분모(기준 중량)와 섭취방법(`NTK_MTHD`) 연결, 1일 함량 = 함량 × (1일 섭취 중량 ÷ 분모)
   - 정할 것: 분모의 정 수(`2400mg(2정)`, `5.0g 20정`)를 저장할지. 지금 파서는 버린다. 섭취방법이 정 수로만 적힌 제품(스파이크 200개 중 138개가 계산 불가)을 잇는 데 필요할 수 있다
   - 단위 환산도 이 단계: `억`·`X 10^n` 배수, `㎍/μg` 통일. `cells`(사균)와 `CFU`는 섞지 않는다
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
   - jar artifact 전달은 Dockerfile 방식이 정해진 뒤 판단. 멀티 모듈이라 경로는 `api/build/libs`, `-plain.jar` 제외

## 완료

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

### 2026-10-31
- 검수 대상 판단 (`feat/parser-review-flags`). V2 엔티티에서 `NEEDS_REVIEW`를 정하려면 파서가 "못 읽음"을 알려야 해서 엔티티보다 먼저 한다. 엔티티는 사용자가 직접 작성하기로 해서 다음 PR로 나눔.
  - `parse()`가 `ParseResult(amounts, unreadLines)`를 돌려준다. 못 읽은 줄: 느슨한 함량 모양(숫자에 아무 쉼표·마침표, 분모 단위 무관)이 엄격한 패턴 매치 밖에 있는 줄. 일부러 버린 값(오염물질 `mg/kg`, 이하 상한, 세균수)과 원료성 제품 줄은 넣지 않는다.
  - 처음엔 18,702개 제품이 잡혔다. `1. 납(mg/kg)`의 항목 번호를 함량으로 읽어서다. 단위 자리에 괄호·콜론·한글을 받지 않게 해 246개로 줄임. 남은 오탐은 `세균수 : 1ml/당 100`, `1㎎/㎏ 이하` 류 44줄로 검수에서 거른다.
  - `AmountSanityCheck`: `1.600 mg`은 정상 숫자로 읽혀 못 읽은 줄이 아니다. 숫자 하나로는 오기인지 알 수 없어(소수점 3자리 규칙은 정상 값 포함 999개 제품) 값끼리 모순을 본다. 함량 > 분모(질량 단위끼리), 같은 제품 분모의 1000배 차이. 단위 오기(`2.4g/200mg`, `500mg`/`500g`)도 잡힌다. 값은 고치지 않는다.
  - 결과(I0030 46,057건): 못 읽은 줄 246개 제품, 이상치 56개 제품, 합쳐 299개(0.65%). 이상치 표본은 모두 실제 오기. 골든 200건에서 검수로 가는 제품 = 알려진 실패 3개.
  - 분모가 정(`0.138mg/4정(2,000mg)`)인 줄도 못 읽은 줄로 간다. 1일 기준 환산 단계에서 정 수를 저장하면 읽을 수 있다.
  - 정리: 노션 「함량 이상치 검사: 고치지 않고 검수로 보내기」

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
