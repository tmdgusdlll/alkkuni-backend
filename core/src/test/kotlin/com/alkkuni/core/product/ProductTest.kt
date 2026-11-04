package com.alkkuni.core.product

import com.alkkuni.core.parser.IngredientAmountParser
import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductTest {

    @Test
    fun `함량을 읽으면 PARSED`() {
        val product = apply("2. 비타민E : 표시량(17mg/2g)의 80~150%")
        assertEquals(ParseStatus.PARSED, product.parseStatus)
        assertNull(product.parseIssue)
    }

    @Test
    fun `함량이 없으면 NO_AMOUNT`() {
        assertEquals(ParseStatus.NO_AMOUNT, apply("1. 성상 : 고유의 향미가 있는 분말").parseStatus)
        assertEquals(ParseStatus.NO_AMOUNT, apply(null).parseStatus)
    }

    @Test
    fun `함량이 분모보다 크면 NEEDS_REVIEW이고 사유를 남긴다`() {
        val product = apply("2. 비타민C : 표시량(2.4g/200mg)")
        assertEquals(ParseStatus.NEEDS_REVIEW, product.parseStatus)
        assertTrue(product.parseIssue!!.contains("함량이 분모보다 큼"))
    }

    @Test
    fun `못 읽은 줄이 있으면 함량이 없어도 NEEDS_REVIEW`() {
        // 소수점 쉼표는 추측하지 않고 버린다(PR #8). 함량 모양이라 못 읽은 줄로 남는다.
        val product = apply("2. 아연 : 표시량(5,5mg/1g)")
        assertEquals(ParseStatus.NEEDS_REVIEW, product.parseStatus)
        assertTrue(product.parseIssue!!.contains("못 읽은 줄"))
    }

    @Test
    fun `원문이 같으면 검수 결과를 덮어쓰지 않는다`() {
        val text = "2. 아연 : 표시량(5,5mg/1g)"
        val product = reviewed(text)

        assertFalse(product.applyParse(text, IngredientAmountParser.parse(text)))
        assertEquals(ParseStatus.REVIEWED, product.parseStatus)
        // 다시 판정하지 않았으니 버전도 그대로다
        assertEquals("0", product.parserVersion)
    }

    @Test
    fun `원문이 바뀌면 검수했어도 다시 판정한다`() {
        val product = reviewed("2. 아연 : 표시량(5,5mg/1g)")
        val newText = "2. 아연 : 표시량(5.5mg/1g)"

        assertTrue(product.applyParse(newText, IngredientAmountParser.parse(newText)))
        assertEquals(ParseStatus.PARSED, product.parseStatus)
        assertEquals(newText, product.standardText)
        assertEquals(IngredientAmountParser.VERSION, product.parserVersion)
    }

    @Test
    fun `원료 여부도 applyParse가 기준규격으로 정한다`() {
        assertTrue(apply("2. 진세노사이드 합 *원료성제품 표시량(30mg/g)의 100% 이상").rawMaterial)
        assertFalse(apply("(가) 원료성 : 표시량(3mg/g)이상 (나) 최종제품 표시량(3mg/g)의 80%이상").rawMaterial)
        assertFalse(apply(null).rawMaterial)
    }

    @Test
    fun `1일 횟수 곱하기 1회 중량이 분모와 같으면 DAILY`() {
        val product = apply("비타민C : 표시량(500mg/2,400mg)", "1일 2회, 1회 2캡슐(1,200mg)")
        assertEquals(BasisCheck.DAILY, product.basisCheck)
        assertEquals(2, product.intakeTimesPerDay)
        assertEquals("캡슐", product.intakeUnit)
    }

    @Test
    fun `분모와 1회 중량의 단위가 달라도 같은 종류면 비교한다`() {
        // 2.4g = 2회 × 1,200mg, 반올림 2% 이내(1,190mg)도 같다고 본다
        assertEquals(BasisCheck.DAILY, apply("비타민C : 표시량(500mg/2.4g)", "1일 2회, 1회 2캡슐(1,190mg)").basisCheck)
    }

    @Test
    fun `1회 중량이 분모와 같으면 PER_SERVING`() {
        assertEquals(BasisCheck.PER_SERVING, apply("비타민C : 표시량(500mg/1,200mg)", "1일 2회, 1회 2캡슐(1,200mg)").basisCheck)
    }

    @Test
    fun `하루치도 1회분도 아니면 MISMATCH`() {
        assertEquals(BasisCheck.MISMATCH, apply("비타민C : 표시량(500mg/1g)", "1일 2회, 1회 2캡슐(1,200mg)").basisCheck)
    }

    @Test
    fun `액상은 ml끼리 비교한다`() {
        assertEquals(BasisCheck.DAILY, apply("비타민C : 표시량(100mg/500mL)", "1일 5회, 1회 1포(100mL)를 섭취").basisCheck)
    }

    @Test
    fun `1회 중량이 없거나 무게와 부피가 섞이면 UNVERIFIED`() {
        assertEquals(BasisCheck.UNVERIFIED, apply("비타민C : 표시량(500mg/2,400mg)", "1일 2회, 1회 2캡슐").basisCheck)
        // 밀도를 몰라 mg와 ml는 비교하지 않는다
        assertEquals(BasisCheck.UNVERIFIED, apply("비타민C : 표시량(100mg/20mL)", "1일 1회, 1회 1병(20g)").basisCheck)
    }

    @Test
    fun `섭취방법을 못 읽으면 UNREADABLE, 함량이 없으면 null`() {
        assertEquals(BasisCheck.UNREADABLE, apply("비타민C : 표시량(500mg/2,400mg)", "1일 1~2회 섭취").basisCheck)
        assertNull(apply("1. 성상 : 고유의 향미가 있는 분말", "1일 2회, 1회 2캡슐(1,200mg)").basisCheck)
    }

    @Test
    fun `검수해서 기준규격 판정을 건너뛰어도 섭취방법은 파싱한다`() {
        val text = "2. 아연 : 표시량(5,5mg/1g)"
        val product = Product(
            reportNo = "1", name = "제품", intakeMethod = "1일 1회, 1회 1정", standardText = text, rawMaterial = false,
            parseStatus = ParseStatus.REVIEWED, parserVersion = "0",
        )
        assertFalse(product.applyParse(text, IngredientAmountParser.parse(text)))
        assertEquals(1, product.intakeTimesPerDay)
    }

    private fun apply(text: String, intake: String) =
        Product(reportNo = "1", name = "제품", intakeMethod = intake, rawMaterial = false, parseStatus = ParseStatus.NO_AMOUNT, parserVersion = "0")
            .apply { assertTrue(applyParse(text, IngredientAmountParser.parse(text))) }

    private fun apply(text: String?) = newProduct(ParseStatus.NO_AMOUNT).apply {
        assertTrue(applyParse(text, IngredientAmountParser.parse(text ?: "")))
    }

    private fun reviewed(text: String) = Product(
        reportNo = "1", name = "제품", standardText = text, rawMaterial = false, parseStatus = ParseStatus.REVIEWED,
        parserVersion = "0",
    )

    private fun newProduct(status: ParseStatus) =
        Product(reportNo = "1", name = "제품", rawMaterial = false, parseStatus = status, parserVersion = "0")
}
