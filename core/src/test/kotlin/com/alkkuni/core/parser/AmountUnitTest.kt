package com.alkkuni.core.parser

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountUnitTest {

    @Test
    fun `질량은 mg로 바꾼다`() {
        assertNormalized("1500", "mg", "1.5", "g")
        assertNormalized("1.5", "mg", "1.5", "㎎")
        for (unit in listOf("㎍", "μg", "µg", "ug", "mcg")) assertNormalized("0.01", "mg", "10", unit)
    }

    @Test
    fun `비타민 환산 단위는 표기 변형을 하나로 모은다`() {
        for (unit in listOf("mg α-TE", "mgα-TE", "mg a-TE", "mga-TE", "㎎ɑ-TE", "mg-aTE", "mg α -TE", "mg 𝛂-TE", "mg alpha-TE", "mg DLα-TE")) assertNormalized("10", "mg α-TE", "10", unit)
        for (unit in listOf("mg NE", "mgNE", "㎎ NE", "mg-NE")) assertNormalized("15", "mg NE", "15", unit)
        for (unit in listOf("μg RAE", "ugRAE", "㎍ RAE", "μgREA")) assertNormalized("0.7", "mg RAE", "700", unit)
        for (unit in listOf("㎍RE", "ug RE", "µgRE")) assertNormalized("0.7", "mg RE", "700", unit)
        for (unit in listOf("ug DFE", "㎍DFE", "μg DEF", "mcgDFE")) assertNormalized("0.4", "mg DFE", "400", unit)
    }

    @Test
    fun `균수는 억과 10의 거듭제곱을 곱해 개수로 바꾼다`() {
        assertNormalized("100000000", "CFU", "100000000", "CFU")
        assertNormalized("100000000", "CFU", "100000000", "cfu")
        assertNormalized("300000000", "CFU", "3", "억 CFU")
        assertNormalized("300000000", "CFU", "3", "억cfu")
        assertNormalized("100000000000", "CFU", "1", "X 10^11 CFU")
        assertNormalized("2000000000", "CFU", "2", "x10^9CFU")
        assertNormalized("500000000", "CFU", "5", "*10^8 CFU")
        assertNormalized("100000000", "CFU", "1", "×10^8c.f.u")
        assertNormalized("1000000000", "CFU", "1", "X 10^09 CFU")
    }

    @Test
    fun `사균(cells)은 CFU와 섞지 않는다`() {
        assertNormalized("200000000", "cells", "2", "억 cells")
        assertNormalized("10000000000", "cells", "1", "*10^10cells")
    }

    @Test
    fun `환산할 수 없거나 알 수 없는 단위는 null`() {
        // IU·U·FU·개는 성분마다 환산이 달라 성분 사전 단계에서 다룬다. 단독 억은 무엇의 억인지 모른다.
        for (unit in listOf("IU", "U", "FU", "개", "억", "㎍g", "㎍mg", "m", "ml", "mg E", "mgα", "^9 CFU", "×10^9", "cuf", "MPN")) {
            assertNull(AmountUnit.normalize(BigDecimal.ONE, unit), unit)
        }
    }

    @Test
    fun `toMg는 환산 단위 표시를 떼고 질량만 본다`() {
        assertEquals(0, BigDecimal("0.7").compareTo(AmountUnit.toMg(BigDecimal("700"), "μg RAE")))
        assertEquals(0, BigDecimal("10").compareTo(AmountUnit.toMg(BigDecimal("10"), "mga-TE")))
        assertNull(AmountUnit.toMg(BigDecimal.ONE, "CFU"))
        // 1일 함량에는 쓰지 않는 모르는 접미사도 이상치 검사에서는 질량으로 본다(리뷰 전 규칙 유지)
        assertEquals(0, BigDecimal("2").compareTo(AmountUnit.toMg(BigDecimal("2"), "mg E")))
        assertNull(AmountUnit.normalize(BigDecimal("2"), "mg E"))
    }

    private fun assertNormalized(expected: String, expectedUnit: String, value: String, unit: String) {
        val (v, u) = AmountUnit.normalize(BigDecimal(value), unit) ?: error("null: $unit")
        assertEquals(0, BigDecimal(expected).compareTo(v), "$value $unit → $v")
        assertEquals(expectedUnit, u, unit)
    }
}
