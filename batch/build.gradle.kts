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
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    // 적재 테스트용 DB. batch에는 Flyway가 없어 테스트에서만 api의 마이그레이션 파일로 스키마를 만든다.
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

// 루트 .env를 local 프로필에서 읽도록 작업 디렉터리를 레포 루트로 맞춘다.
tasks.bootRun {
    workingDir = rootDir
}

// Dockerfile이 모듈 이름으로 jar를 찾는다(-plain.jar와 섞이지 않게 이름을 고정).
tasks.bootJar {
    archiveFileName = "batch.jar"
}
