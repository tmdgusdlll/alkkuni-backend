# PROGRESS.md

알꾼이 백엔드의 진행 상황이다. **작업 PR 안에서 갱신하고, 머지되면 확정된다.** 머지 후 문서만 고치는 변경을 만들지 않는다.

- PR을 올리기 전: 작업 커밋에 결정 이유, 다음 할 일 정리 등 이미 아는 내용을 넣는다.
- PR을 올린 뒤 머지 전: CI 결과, PR 번호처럼 PR이 있어야 아는 내용만 같은 브랜치에 커밋을 더한다.


- 지금 상태: 이번 주차 목차. 체크 안 된 첫 항목이 다음 작업이다. 해당 PR에서 체크한다.
- 다음 할 일: 항목별로 정할 것과 검증 기준. 끝난 항목은 지우고 "완료"에 날짜와 함께 옮긴다.
- 작업 로그: 결정과 그 이유. 최근 날짜가 위에 온다.

## 지금 상태

### 1주차 체크리스트 — 백엔드 레포 골격, CI, 리뷰 봇

- [x] `core` / `api` / `batch` 멀티 모듈 (PR #1)
- [x] 로컬 PostgreSQL compose, yaml 공통/local/prod 분리 (PR #2)
- [x] Flyway 첫 마이그레이션 + Testcontainers 컨텍스트 테스트 (PR #5)
- [x] GitHub Actions CI, 브랜치 보호에 CI 필수 (PR #6)
- [ ] CodeRabbit 연결

## 다음 할 일

1. CodeRabbit 연결
   - 할 것: CodeRabbit 앱 설치와 `.coderabbit.yaml`
   - 검증: 이 PR에서 `@coderabbitai review` 응답 확인
2. (2주차) 스파이크 검수 200개로 파서 골든 테스트
3. (2주차) 배포 준비: 도메인 확보, SES 샌드박스 해제·소셜 로그인 검수에 걸리는 기간 확인
4. (3주차 첫 작업) ECR 푸시 → EC2 기본 배포. 배포가 처음 필요한 시점이 3주차 CloudFront라 미룸
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

### 2026-10-29
- CodeRabbit 연결 (`chore/coderabbit`)
  - `@claude` GitHub 연동은 하지 않기로 함. 1인 개발이라 질문은 로컬 Claude Code로 충분하고, 연결하면 시크릿과 워크플로 파일(위험 경로)만 늘어난다. (`PLANNING.md` "리뷰 구조")
  - `.coderabbit.yaml`: 한국어 리뷰, 자동 리뷰 끔(`@coderabbitai review`로 수동 요청).
  - CodeRabbit도 로컬과 같은 `REVIEW.md`·`CLAUDE.md` 기준으로 리뷰한다. 기본 설정(`code_guidelines`)이 두 파일을 읽으므로 따로 지정하지 않는다. 기준을 빼면 넓게 보지만 이미 정한 규칙(엔티티 `data class` 금지 등)과 부딪히는 오탐 처리 비용이 더 크다고 봄. 관점 차이는 기준보다 도구·모델 차이에서 나온다.
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
