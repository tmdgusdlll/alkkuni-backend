package com.alkkuni.batch.raw

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 받은 응답 본문을 가공하지 않고 그대로 저장한다. DB는 이 원본으로 다시 만들 수 있어야 한다(ADR-002).
 * 저장에 실패하면 예외를 던진다. 원본 없이 적재를 이어가지 않는다.
 * 구현은 `alkkuni.raw-store.type`으로 고른다: local(기본, 개발·테스트) / s3(운영).
 */
interface RawStore {
    fun save(service: String, collectedAt: LocalDateTime, name: String, body: String)
}

private val FOLDER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/** 두 구현이 같은 모양으로 저장하게 하는 상대 경로: {service}/{수집 시각}/{name} */
internal fun rawPath(service: String, collectedAt: LocalDateTime, name: String) =
    "$service/${collectedAt.format(FOLDER)}/$name"
