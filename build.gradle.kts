import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// 플러그인 버전은 여기서 한 번만 정하고, 실제 적용은 각 모듈이 한다.
plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    kotlin("plugin.jpa") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.2" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// 모든 모듈 공통 설정
subprojects {
    group = "com.alkkuni"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    // Kotlin 플러그인을 적용한 모듈에만 Java 버전과 컴파일러 옵션을 건다.
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
        configure<KotlinJvmProjectExtension> {
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }
        dependencies {
            "implementation"("org.jetbrains.kotlin:kotlin-reflect")
            "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
