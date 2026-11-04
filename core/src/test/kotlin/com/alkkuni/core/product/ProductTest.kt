package com.alkkuni.core.product

import com.alkkuni.core.parser.IngredientAmountParser
import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import java.math.BigDecimal
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
        // 섭취방법은 새 규칙으로 판정했으니 버전은 올린다. 그대로면 적재 때마다 다시 처리된다.
        assertEquals(IngredientAmountParser.VERSION, product.parserVersion)
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
    fun `섭취방법이 원료용이고 하루 섭취량이 없으면 원료다`() {
        val text = "프로바이오틱스 수 : 표시량(1 X 10^11 CFU/g) 이상"
        assertTrue(apply(text, "① 건강기능식품 제조 시 일일 섭취량에 적합한 양을 사용").rawMaterial)
        assertTrue(apply(text, "건강기능식품 원료로 사용").rawMaterial)
        assertTrue(apply(text, "건강기능식품 제조 시 1일 섭취량에 적합한 양을 사용").rawMaterial)
        // 원료 판매와 소비자 섭취를 함께 적은 제품은 완제품으로도 팔리므로 원료로 보지 않는다
        assertFalse(apply(text, "1일 3회, 1회 1g을 물과 함께 섭취하십시오. 건강기능식품원료로 적당량 사용.").rawMaterial)
        assertFalse(apply(text, "건강기능식품 원료로 사용. 1회 1g씩 그대로 또는 물에 타서 섭취하세요.").rawMaterial)
        assertFalse(apply(text, "1일 1회, 1회 1포(2g)").rawMaterial)
    }

    @Test
    fun `원료는 1일 함량이 없다`() {
        val text = "진세노사이드 : 표시량(12mg/g)"
        val product = apply(text, "건강기능식품 원료로 사용")
        assertNull(product.dailyAmount(IngredientAmountParser.parse(text).amounts.single()))
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
    fun `성분마다 분모 표기가 달라도 값이 같으면 하나로 본다`() {
        val text = "1. 비타민C : 표시량(500mg/2,400mg)\n2. 아연 : 표시량(8.5mg/2.4g)"
        assertEquals(BasisCheck.DAILY, apply(text, "1일 2회, 1회 2캡슐(1,200mg)").basisCheck)
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

    @Test
    fun `1일 함량 - 분모가 하루치면 함량 그대로`() {
        assertDaily("3000", "mg", "비타민C : 표시량(3000mg/3g)", "1일 1회, 1회 1포(3g)")
    }

    @Test
    fun `1일 함량 - 분모가 1회분이면 1일 횟수를 곱한다`() {
        assertDaily("1000", "mg", "비타민C : 표시량(500mg/2.0g)", "1일 2회, 1회 1포(2.0g)")
    }

    @Test
    fun `1일 함량 - 분모가 단위당(g)이면 하루 섭취 중량을 곱한다`() {
        // 12mg/g × (3g × 3회 = 9g). 제품 판정은 MISMATCH지만 농도 표기라 계산할 수 있다.
        assertDaily("108", "mg", "진세노사이드 : 표시량(12mg/g)", "1일 3회, 1회 3g")
    }

    @Test
    fun `1일 함량 - 섭취 중량이 없고 분모가 전체 중량이면 분모를 하루치로 가정한다`() {
        assertDaily("100", "mg", "비타민C : 표시량(100mg/1600mg)", "1일 1회, 1회 2정")
        // 섭취방법을 못 읽어도(UNREADABLE) 같은 가정이다
        assertDaily("100", "mg", "비타민C : 표시량(100mg/1600mg)", "1일 1~2회 섭취")
    }

    @Test
    fun `1일 함량 - 단위를 통일한다`() {
        assertDaily("0.7", "mg RAE", "비타민A : 표시량(700㎍ RAE/2g)", "1일 1회, 1회 1포(2g)")
        assertDaily("200000000", "CFU", "프로바이오틱스 수 : 표시량(1억 CFU/g)", "1일 1회, 1회 1포(2g)")
    }

    @Test
    fun `1일 함량 - 계산할 수 없으면 null`() {
        // 단위당 분모인데 1회 중량이 없다(1캡슐이 몇 g인지 모름)
        assertDaily(null, null, "비타민B1 : 표시량(1.2mg/g)", "1일 1회, 1회 2캡슐")
        // 단위당 분모인데 1회 섭취가 부피라 무게로 바꿀 수 없다
        assertDaily(null, null, "프로폴리스 : 표시량(2mg/g)", "1일 1회, 1회 0.5mL")
        // 분모가 하루치도 1회분도 아니다(원문 오류 가능성, 검수 대상)
        assertDaily(null, null, "진세노사이드 : 표시량(12mg/20g)", "1일 2회, 1회 1스푼(5g)")
        // 환산할 수 없는 단위
        assertDaily(null, null, "비타민D : 표시량(400IU/2g)", "1일 1회, 1회 1포(2g)")
        // "/1.000mg"(1,000mg의 오기)을 1mg당 농도로 보면 1000배가 된다. mg 분모는 농도로 보지 않는다(백세시크릿 부아메라)
        assertDaily(null, null, "루테인 : 표시량(10mg/1.000mg)", "1일 2회, 1회 1캡슐(500mg)")
        // 1회 중량 "2.000 mg"은 2mg인지 2,000mg인지 몰라 비우므로 /g 분모를 계산하지 않는다(덴티솔루션)
        assertDaily(null, null, "총 플라보노이드 : 표시량(17mg/1g)", "1일 1회, 1회 2정(2.000 mg)을 충분한 물과 함께 섭취하십시오.")
        // 분모 0은 원문 오기라 하루치로 가정하지 않는다
        assertDaily(null, null, "비타민C : 표시량(100mg/0mg)", "1일 1회, 1회 2정")
    }

    private fun assertDaily(expected: String?, expectedUnit: String?, text: String, intake: String) {
        val product = apply(text, intake)
        val daily = product.dailyAmount(IngredientAmountParser.parse(text).amounts.single())
        assertEquals(expected?.let(::BigDecimal)?.stripTrailingZeros(), daily?.first?.stripTrailingZeros(), "$text / $intake")
        assertEquals(expectedUnit, daily?.second)
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
