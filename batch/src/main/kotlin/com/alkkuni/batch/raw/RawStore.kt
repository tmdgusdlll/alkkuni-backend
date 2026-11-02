package com.alkkuni.batch.raw

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 받은 응답 본문을 가공하지 않고 그대로 저장한다. DB는 이 원본으로 다시 만들 수 있어야 한다.
 * 경로: {dir}/{service}/{수집 시각}/{name}
 */
// ponytail: 로컬 파일 시스템만 지원. 운영 배포(3주차) 때 S3 저장을 붙인다.
@Component
class RawStore(@Value("\${alkkuni.raw-store.dir}") private val dir: Path) {

    fun save(service: String, collectedAt: LocalDateTime, name: String, body: String): Path {
        val target = dir.resolve(service).resolve(collectedAt.format(FOLDER)).resolve(name)
        Files.createDirectories(target.parent)
        return Files.writeString(target, body)
    }

    companion object {
        private val FOLDER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }
}
