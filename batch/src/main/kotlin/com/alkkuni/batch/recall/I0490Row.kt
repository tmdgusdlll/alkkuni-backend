package com.alkkuni.batch.recall

import tools.jackson.databind.JsonNode

/** I0490(회수·판매중지) 한 행에서 저장에 쓰는 값만 꺼낸 것. API의 ""는 null로 바꾼다. */
data class I0490Row(
    val sourceSeq: String,
    val reportNo: String?,
    val productName: String,
    val manufacturer: String?,
    val productType: String?,
    val reason: String?,
    val expiryText: String?,
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
            )
        }
    }
}
