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
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
}

// 루트 .env를 local 프로필에서 읽도록 작업 디렉터리를 레포 루트로 맞춘다.
tasks.bootRun {
    workingDir = rootDir
}
