package com.alkkuni.batch.raw

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

/** 로컬 파일 시스템에 저장한다. 경로: {dir}/{service}/{수집 시각}/{name} */
@Component
@ConditionalOnProperty("alkkuni.raw-store.type", havingValue = "local", matchIfMissing = true)
class LocalRawStore(@Value("\${alkkuni.raw-store.dir}") private val dir: Path) : RawStore {

    override fun save(service: String, collectedAt: LocalDateTime, name: String, body: String) {
        val target = dir.resolve(rawPath(service, collectedAt, name))
        Files.createDirectories(target.parent)
        Files.writeString(target, body)
    }
}
