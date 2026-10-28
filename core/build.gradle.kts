import org.springframework.boot.gradle.plugin.SpringBootPlugin

// 실행 앱이 아니라 api·batch가 가져다 쓰는 라이브러리라 Spring Boot 플러그인을 적용하지 않는다.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

// Boot 플러그인이 없으므로 의존성 버전은 Spring Boot BOM으로 맞춘다.
dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // api: core를 쓰는 모듈도 Entity·Repository 타입을 컴파일할 수 있도록 노출한다.
    api("org.springframework.boot:spring-boot-starter-data-jpa")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
