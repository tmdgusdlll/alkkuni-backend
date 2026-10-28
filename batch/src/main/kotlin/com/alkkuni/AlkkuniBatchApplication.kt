package com.alkkuni

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// 최상위 패키지(com.alkkuni)에 두어야 core의 빈·엔티티·리포지토리까지 기본 스캔 범위에 들어간다.
@SpringBootApplication
class AlkkuniBatchApplication

fun main(args: Array<String>) {
    runApplication<AlkkuniBatchApplication>(*args)
}
