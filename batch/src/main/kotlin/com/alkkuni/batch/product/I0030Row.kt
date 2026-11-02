package com.alkkuni.batch.product

import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.HexFormat

/**
 * I0030(건강기능식품 품목제조 신고사항) 한 행에서 적재에 쓰는 값만 꺼낸 것.
 * API는 값이 없으면 null 대신 ""를 준다. 여기서 null로 바꾼다.
 */
data class I0030Row(
    val reportNo: String,
    val name: String,
    val manufacturer: String?,
    val intakeMethod: String?,
    val standardText: String?,
    val sourceUpdatedAt: LocalDateTime?,
) {
    // 적재에 쓰는 값이 하나라도 바뀌면 달라진다. 같으면 upsert가 그 행을 건너뛴다.
    // 원본 JSON 전체가 아니라 쓰는 값만 넣는다. 안 쓰는 필드 변경으로 재파싱하지 않고, 파일 방식(CSV)과도 같은 값이 나온다.
    val sourceHash: String by lazy {
        val joined = listOf(reportNo, name, manufacturer, intakeMethod, standardText, sourceUpdatedAt)
            .joinToString("\u0000") { it?.toString() ?: "" }
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(joined.toByteArray()))
    }

    companion object {
        fun from(node: JsonNode): I0030Row {
            fun text(field: String) = node.path(field).asString("").trim().ifEmpty { null }
            return I0030Row(
                // 신고번호·제품명은 전체 데이터에 빈 값이 없다. 비어 있으면 upsert 키가 깨지므로 멈춘다.
                reportNo = requireNotNull(text("PRDLST_REPORT_NO")) { "PRDLST_REPORT_NO가 비어 있다" },
                name = requireNotNull(text("PRDLST_NM")) { "PRDLST_NM이 비어 있다: ${text("PRDLST_REPORT_NO")}" },
                manufacturer = text("BSSH_NM"),
                intakeMethod = text("NTK_MTHD"),
                standardText = text("STDR_STND"),
                // YYYYMMDD. 전체 데이터가 8자리라, 다른 모양이 오면 예외로 드러나게 둔다.
                sourceUpdatedAt = text("LAST_UPDT_DTM")
                    ?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() },
            )
        }
    }
}
