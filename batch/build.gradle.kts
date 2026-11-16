plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":core"))
    // 식약처 API 호출. 웹 서버 없이 RestClient만 쓴다.
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    runtimeOnly("org.postgresql:postgresql")
    // 운영 원본 저장(S3RawStore). Spring Boot가 버전을 관리하지 않아 BOM으로 맞춘다.
    // HTTP 클라이언트는 JDK HttpURLConnection 기반(url-connection-client)만 둔다. 페이지마다 순서대로 한 번 올리는 용도라
    // 연결 풀이 필요 없고, 기본으로 딸려오는 netty(비동기)·apache 클라이언트를 빼서 batch jar·메모리를 아낀다.
    implementation(platform("software.amazon.awssdk:bom:2.59.1"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache5-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    // 적재 테스트용 DB. batch에는 Flyway가 없어 테스트에서만 api의 마이그레이션 파일로 스키마를 만든다.
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-localstack")
}

// 루트 .env를 local 프로필에서 읽도록 작업 디렉터리를 레포 루트로 맞춘다.
tasks.bootRun {
    workingDir = rootDir
}

// Dockerfile이 모듈 이름으로 jar를 찾는다(-plain.jar와 섞이지 않게 이름을 고정).
tasks.bootJar {
    archiveFileName = "batch.jar"
}
