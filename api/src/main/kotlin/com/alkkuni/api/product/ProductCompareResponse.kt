package com.alkkuni.api.product

import com.alkkuni.core.ingredient.entity.Ingredient
import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.entity.ProductIngredient
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import java.math.BigDecimal

data class ProductCompareResponse(
    // 요청한 ids 순서. ingredients[*].values의 열 순서와 같다.
    val products: List<CompareProduct>,
    // 성분 행. 여러 제품에 든 성분이 먼저 온다.
    val ingredients: List<CompareIngredient>,
)

data class CompareProduct(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    val form: String?,
    // DAILY·PER_SERVING만 "1일 기준", 나머지는 "표시 기준량"으로 보여준다(검색 API와 같은 규칙).
    val basisCheck: BasisCheck?,
    // NEEDS_REVIEW면 함량이 이상해 검수 대기 중이다. 경고로 구분한다.
    val parseStatus: ParseStatus,
)

data class CompareIngredient(
    // 사전에 없는 성분은 null이고, 원문 이름이 같을 때만 같은 행으로 묶는다.
    val ingredientId: Long?,
    val name: String,
    // 1일 기준값과 단위(성분 목록 API와 같음). 기준값이 없는 성분이나 사전에 없는 성분은 null이다.
    val referenceAmount: BigDecimal?,
    val referenceUnit: String?,
    // products와 같은 순서의 제품별 함량 목록. 없으면 빈 목록("-").
    // 한 제품에 여러 개면 합치지 않는다. 멀티팩(합산이 맞음)과 수출국별·원료용 중복 규격(하나만 맞음)을 데이터로 구분할 수 없다(PLANNING.md).
    val values: List<List<CompareAmount>>,
)

data class CompareAmount(
    // 1일 함량(단위 통일). 계산할 수 없으면 null이고 화면은 원문 값(amount/basis)을 보여준다.
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
    // 기준값 대비 %(소수점 버림). 기준값이 없거나 단위를 비교할 수 없으면 null이고 화면은 표시하지 않는다.
    val referencePercent: Int?,
    // 기준값 이상이면 true(상한 2배 초과는 false). 판정할 수 없으면 null. "표시 기준량" 함량도 판정한다(basisCheck로 구분).
    val meetsReference: Boolean?,
    // 상한 2배 초과. 원문 오기·파싱 오류 의심으로 경고를 단다.
    val exceedsUpperLimit: Boolean,
    val rawName: String,
    val amount: BigDecimal,
    val unit: String,
    val basisAmount: BigDecimal,
    val basisUnit: String,
)

fun Product.toCompareProduct() = CompareProduct(
    id = requireNotNull(id),
    name = name,
    manufacturer = manufacturer,
    form = form,
    basisCheck = basisCheck,
    parseStatus = parseStatus,
)

// ingredient: 연결된 사전 성분. 없으면(사전에 없는 성분) 기준값 판정을 하지 않는다.
fun ProductIngredient.toCompareAmount(ingredient: Ingredient?) = CompareAmount(
    dailyAmount = dailyAmount,
    dailyUnit = dailyUnit,
    referencePercent = ingredient?.referencePercent(dailyAmount, dailyUnit),
    meetsReference = ingredient?.meetsReference(dailyAmount, dailyUnit),
    exceedsUpperLimit = ingredient?.exceedsUpperLimit(dailyAmount, dailyUnit) ?: false,
    rawName = rawName,
    amount = amount,
    unit = unit,
    basisAmount = basisAmount,
    basisUnit = basisUnit,
)
