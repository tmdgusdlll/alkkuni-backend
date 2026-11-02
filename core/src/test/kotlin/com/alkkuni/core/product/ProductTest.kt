package com.alkkuni.core.product

import com.alkkuni.core.parser.IngredientAmountParser
import com.alkkuni.core.product.entity.Product
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
