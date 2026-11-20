package com.alkkuni.batch.recall

import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class I0490RowTest {
    private val mapper = JsonMapper.builder().build()

    private fun row(cretDtm: String, grade: String) = I0490Row.from(
        mapper.readTree("""{"RTRVLDSUSE_SEQ":"1","PRDTNM":"제품","CRET_DTM":"$cretDtm","RTRVL_GRDCD_NM":"$grade"}"""),
    )

    @Test
    fun `등록일은 소수점 자릿수가 달라도 읽고 등급은 원문 그대로 둔다`() {
        // 원본에 소수점 1~6자리가 섞여 온다(2026-11-03 원본)
        assertEquals(LocalDateTime.of(2026, 11, 3, 17, 34, 32, 325_706_000), row("2026-11-03 17:34:32.325706", "1등급").registeredAt)
        assertEquals(LocalDateTime.of(2026, 11, 3, 17, 34, 32, 300_000_000), row("2026-11-03 17:34:32.3", "자율회수").registeredAt)
        assertEquals("자율회수", row("2026-11-03 17:34:32.3", "자율회수").grade)
    }

    @Test
    fun `빈 값이나 읽을 수 없는 등록일은 null이다`() {
        assertNull(row("", "").registeredAt)
        assertNull(row("", "").grade)
        assertNull(row("20261103", "1등급").registeredAt)
    }
}
