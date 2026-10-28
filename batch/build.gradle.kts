plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":core"))
    runtimeOnly("org.postgresql:postgresql")
}

// 루트 .env를 local 프로필에서 읽도록 작업 디렉터리를 레포 루트로 맞춘다.
tasks.bootRun {
    workingDir = rootDir
}
