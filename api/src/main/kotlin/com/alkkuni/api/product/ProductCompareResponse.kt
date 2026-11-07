package com.alkkuni.api.product

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
    // products와 같은 순서의 제품별 함량 목록. 없으면 빈 목록("-").
    // 한 제품에 여러 개면 합치지 않는다. 멀티팩(합산이 맞음)과 수출국별·원료용 중복 규격(하나만 맞음)을 데이터로 구분할 수 없다(PLANNING.md).
    val values: List<List<CompareAmount>>,
)

data class CompareAmount(
    // 1일 함량(단위 통일). 계산할 수 없으면 null이고 화면은 원문 값(amount/basis)을 보여준다.
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
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

fun ProductIngredient.toCompareAmount() = CompareAmount(
    dailyAmount = dailyAmount,
    dailyUnit = dailyUnit,
    rawName = rawName,
    amount = amount,
    unit = unit,
    basisAmount = basisAmount,
    basisUnit = basisUnit,
)
