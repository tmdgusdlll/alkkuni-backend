# PROGRESS.md

알꾼이 백엔드의 진행 상황이다. **작업을 마칠 때마다 갱신한다.**
가장 최근 내용이 위에 오도록 "작업 로그"에 추가하고, "지금 상태"와 "다음 할 일"을 고친다.

## 지금 상태

- **단계:** 1주차. `core` / `api` / `batch` 멀티 모듈 전환 머지 완료 (PR #1).
- **진행 중:** 로컬 DB(compose)와 yaml 분리 PR 리뷰·머지 대기 (브랜치 `chore/local-db`). 테스트가 하나도 없는 상태이며, 다음 할 일 1번에서 컨텍스트 테스트를 다시 만든다.

## 다음 할 일

1. Flyway 첫 마이그레이션과 `/actuator/health` 확인. Testcontainers(PostgreSQL)로 `api` 컨텍스트 테스트 추가 (core 엔티티·리포지토리 스캔 설정도 이때 검증)
2. GitHub Actions CI (build + test, `setup-java`로 JDK 21 지정) + 브랜치 보호에 CI 체크 통과·대화 해결 필수 추가
3. CodeRabbit 연결과 `.coderabbit.yaml`, `/install-github-app`으로 `@claude` 질문용 연결
4. ECR 푸시까지 CI 확장 → EC2 기본 배포
5. (2주차) 스파이크 검수 200개로 파서 골든 테스트

## 완료

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

### 2026-10-27
- 스파이크 마무리: 200개 정확도 검수, 회수 데이터 성격(소비기한이 남은 목록) 확인, CHNG_DT 동작 테스트, 상업 이용 조건 확인. 노션 보고서에 한 줄 결론과 "한눈에 보기" 표 추가.
- 카카오·네이버·구글 로컬 개발용 OAuth 설정 완료. 웹훅·운영 URI·검수 요청은 배포 후로 미룸 (노션 "출시 준비 체크리스트").
- IntelliJ 프로젝트 설정 결정: Spring Boot 제너레이터, Kotlin, Gradle - Kotlin, 그룹·패키지 `com.alkkuni`, Java 21, Jar, YAML.
  초기 의존성: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Actuator. Security·OAuth2 Client는 로그인 단계에서 추가.
- Claude Code 협업용 `CLAUDE.md`, `docs/PLANNING.md`, `docs/PROGRESS.md` 작성.
- GitHub 레포 `alkkuni-backend` 생성, 첫 커밋 푸시.

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
  - batch에는 Flyway 의존성이 없어 따로 끌 설정이 없다(마이그레이션은 api만).
  - 확인: `:api:bootRun` → `/actuator/health` UP(PostgreSQL 17.11 연결), `:batch:bootRun` 기동, `./gradlew build` 성공.
