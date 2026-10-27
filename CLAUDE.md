# CLAUDE.md

알꾼이 백엔드 레포에서 Claude Code가 작업할 때 따르는 규칙과 맥락이다.
무엇을 만들고 왜 그렇게 정했는지는 `docs/PLANNING.md`, 지금 어디까지 왔는지는 `docs/PROGRESS.md`에 있다.
**작업을 시작할 때 두 파일을 먼저 읽는다.**

## 프로젝트 한 줄 요약

식약처 공공데이터(식품안전나라)로 영양제(건강기능식품) 함량을 1일 기준으로 정규화해 비교하고,
사용자가 서랍에 담은 제품이 회수되면 이메일로 알려주는 웹 서비스. 수익 모델은 광고와 쿠팡 파트너스 제휴 링크.

- 서비스 이름: **알꾼이** (노션 설계 문서에는 아직 "알꾼"으로 표기된 곳이 있음)
- 개발자: 현이 (Java/Spring 경험이 있는 주니어 백엔드 개발자, Kotlin은 이 프로젝트로 처음 본격 사용)
- 이 레포: 백엔드(`alkkuni-backend`). 프론트(Next.js)는 별도 레포

## 기술 스택

- Kotlin 100%, Spring Boot (MVC + Data JPA), Java 21, Gradle Kotlin DSL
- PostgreSQL, Flyway(마이그레이션은 API 배포가 책임)
- Gradle 멀티 모듈: `core` / `api` / `batch`
- 인프라: EC2 t4g.small 1대에 Nginx·Next.js·API·PostgreSQL을 Docker Compose로 운영, 배치는 필요할 때만 뜨는 컨테이너
- AWS: S3(원본·백업), ECR, CloudFront, SES, Parameter Store, Route 53, CloudWatch
- CI/CD: GitHub Actions → ECR → SSM Run Command로 EC2 컨테이너 교체 (OIDC로 IAM 역할 사용)

## 모듈 구조

| 모듈 | 내용 | 의존 |
|---|---|---|
| `core` | Entity, Repository, 정규화 규칙 등 공용 도메인 로직 | 없음 |
| `api` | Controller, 요청·응답 DTO, API용 Service, 인증 | `core` |
| `batch` | 공공데이터 수집, 정규화, 회수 감지 | `core` |

패키지 뿌리는 `com.alkkuni`, 모듈별로 `com.alkkuni.core`, `com.alkkuni.api`, `com.alkkuni.batch`.
`core`는 DTO나 웹 계층을 알면 안 된다. Entity → DTO 변환은 `api` 모듈의 확장 함수로 한다.

## 코드 규칙

**Kotlin**
- Lombok을 쓰지 않는다.
- 의존성은 기본 생성자로 주입한다. (`class FooService(private val repo: FooRepository)`)
- JPA 엔티티는 일반 `class`로 만든다. **`data class` 금지.** `equals`/`hashCode`가 필요하면 ID 기준으로 직접 작성하고, `toString`에 연관관계 필드를 넣지 않는다.
- JPA용 기본 생성자와 open 클래스는 `kotlin-jpa`, `kotlin-spring` 컴파일러 플러그인에 맡긴다.
- 요청·응답 DTO는 `data class`로 만든다.
- DTO 검증 어노테이션은 `@field:NotBlank`처럼 `@field:` 위치 지정을 붙인다.
- null 가능성은 타입으로 표현한다(`Foo?`). 조회는 `findByIdOrNull` 등을 쓰고 `!!`는 근거가 있을 때만 쓴다.
- WebFlux, 코루틴은 쓰지 않는다. (Spring MVC 동기 모델)

**데이터**
- 공공 API 원본은 그대로 S3에 보존하고, 정규화 결과는 DB에 따로 저장한다.
- 정규화 값마다 원문, 추출 방식(규칙/LLM/사람 검수), 확신도, 로직 버전을 함께 저장한다.
- 수집은 멱등이어야 한다. 같은 원본을 다시 처리해도 결과가 같아야 한다(자연키 upsert, 콘텐츠 해시).
- 회수 알림은 Outbox 패턴으로 발송하고 (회수 건, 사용자) 멱등 키로 중복을 막는다.

**API 변경**
- 프론트와 API는 따로 배포되므로 API 변경은 추가 위주로 한다. 제거·이름 변경은 양쪽 호환 단계를 거친다.
- DB 컬럼도 추가 위주로 바꾼다.

## 보안 규칙 (반드시 지킨다)

- API 키, OAuth Client Secret, DB 비밀번호를 코드·설정 파일·커밋에 넣지 않는다. 로컬은 `.env`(Git 제외), 운영은 Parameter Store.
- `.env`, `data/`, `out/` 같은 로컬 데이터 폴더는 `.gitignore`에 둔다.
- 쿠팡 파트너스 등 외부 키는 서버에서만 사용한다.
- 사용자에게 키 값을 대화에 붙여넣으라고 요청하지 않는다.

## 외부 연동 현황 (로컬 개발용 설정 완료)

| 제공자 | 리다이렉트 URI (로컬) | 받는 정보 |
|---|---|---|
| 카카오 | `http://localhost:8080/login/oauth2/code/kakao` | 이메일(필수), 닉네임(선택) |
| 네이버 | `http://localhost:8080/login/oauth2/code/naver` | 이메일(필수), 별명 |
| 구글 | `http://localhost:8080/login/oauth2/code/google` | `openid`, `email`, `profile` |

세 곳 모두 개발·테스트 상태라 등록된 본인 계정으로만 로그인된다. 운영 전환 작업은 노션 "출시 준비 체크리스트"에 있다.

## 작업 방식

- 사용자와 한국어로 대화한다.
- 사용자는 Kotlin을 배우는 중이다. **Java와 다른 Kotlin 문법이나 관례가 처음 나오면 Java 코드와 나란히 짧게 설명한다.**
- 확인하지 않은 사실을 단정하지 않는다. 라이브러리 버전·외부 서비스 정책처럼 바뀔 수 있는 것은 "확인 필요"로 표시한다.
- 설계 문서와 다른 방향으로 가야 할 것 같으면 구현 전에 먼저 말하고 이유를 설명한다.
- 큰 변경은 작은 단위로 나눠 진행하고, 각 단위가 빌드·테스트를 통과하는 상태로 끝낸다.
- **작업을 마칠 때 `docs/PROGRESS.md`를 갱신한다.** (완료한 일, 다음 할 일, 새로 생긴 결정이나 미해결 질문)
- 설계 결정이 바뀌면 `docs/PLANNING.md`에도 반영하고, 노션 설계 문서·ADR 갱신이 필요하다고 사용자에게 알린다.

## 자주 쓰는 명령

> 레포 골격을 만든 뒤 실제 명령으로 채운다.

```bash
./gradlew build                 # 전체 빌드 + 테스트
./gradlew :api:bootRun          # API 로컬 실행
docker compose up -d db         # 로컬 PostgreSQL
```

## 참고 문서 (노션)

- 설계 문서 v0.1: https://app.notion.com/p/405b42bce2db81dcb02cffa64458de79
- 스파이크 계획 및 보고서: https://app.notion.com/p/405b42bce2db81cdbd67ee580284b517
- 출시 준비 체크리스트: https://app.notion.com/p/406b42bce2db8193883aeb43bac247bf
- ADR 목록: 노션 메인 페이지 https://app.notion.com/p/405b42bce2db806b8ac1ecc35c84884c 의 ADR 토글
