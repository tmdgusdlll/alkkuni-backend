package com.alkkuni.batch.recall

import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/** I0490(회수·판매중지) 한 행에서 저장에 쓰는 값만 꺼낸 것. API의 ""는 null로 바꾼다. */
data class I0490Row(
    val sourceSeq: String,
    val reportNo: String?,
    val productName: String,
    val manufacturer: String?,
    val productType: String?,
    val reason: String?,
    val expiryText: String?,
    val registeredAt: LocalDateTime?,
    val grade: String?,
) {
    companion object {
        private val log = LoggerFactory.getLogger(I0490Row::class.java)

        private fun parseRegisteredAt(raw: String): LocalDateTime? =
            runCatching { LocalDateTime.parse(raw.replace(' ', 'T')) }
                .onFailure { log.warn("CRET_DTM을 읽지 못해 등록일을 비운다: {}", raw) }
                .getOrNull()

        fun from(node: JsonNode): I0490Row {
            fun text(field: String) = node.path(field).asString("").trim().ifEmpty { null }
            val seq = requireNotNull(text("RTRVLDSUSE_SEQ")) { "RTRVLDSUSE_SEQ가 비어 있다" }
            return I0490Row(
                sourceSeq = seq,
                reportNo = text("PRDLST_REPORT_NO"),
                productName = requireNotNull(text("PRDTNM")) { "PRDTNM이 비어 있다: $seq" },
                manufacturer = text("BSSHNM"),
                productType = text("PRDLST_TYPE"),
                reason = text("RTRVLPRVNS"),
                expiryText = text("DISTBTMLMT"),
                // "2026-11-03 17:34:32.325706"처럼 오고 소수점 자릿수(1~6)가 제각각이다. ISO 형식은 0~9자리를 모두 읽는다.
                // 형식이 바뀌어도 회수 수집 전체를 멈추지 않게 null로 두고(기존 값은 Recall.seen이 지킨다) 경고를 남긴다
                registeredAt = text("CRET_DTM")?.let(::parseRegisteredAt),
                grade = text("RTRVL_GRDCD_NM"),
            )
        }
    }
}
