package com.alkkuni.batch.recall

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
                // 형식이 바뀌어도 회수 수집 전체를 멈추지 않게 null로 둔다. 배포 다음 날 LISTED 행의 null 개수로 확인한다
                registeredAt = text("CRET_DTM")?.let { runCatching { LocalDateTime.parse(it.replace(' ', 'T')) }.getOrNull() },
                grade = text("RTRVL_GRDCD_NM"),
            )
        }
    }
}
