package com.alkkuni.api.product

import com.alkkuni.api.brand.BrandRef
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
    // 제품명에서 찾은 브랜드. 없으면 null(대부분)
    val brand: BrandRef?,
    // 수출용 제품(제품명 표기로 판정). 화면은 "수출용" 배지를 단다. 기본 정렬에서 국내 제품 뒤에 온다.
    val exportOnly: Boolean,
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
    // 주 단위(성분 목록 API와 같음). 값의 dailyUnit이 이것과 다르면 화면은 "다른 단위 표기"로 막대·강조에서 뺀다. 단위가 섞이지 않는 성분과 사전에 없는 성분은 null이다.
    val defaultUnit: String?,
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
    // 기준값 이상이면 true, 미만이면 false. 판정하지 않으면 null(기준값 없음, 비교 못 하는 단위, 상한 2배 초과 오류 의심).
    // 행마다 따로 판정한다. 같은 성분 행이 여러 개(멀티팩 등)여도 합치지 않는다(PLANNING.md "함량 표시 규칙").
    // "표시 기준량" 함량도 판정한다(basisCheck로 구분).
    val meetsReference: Boolean?,
    // 상한 2배 초과. 원문 오기·파싱 오류 의심으로 경고를 단다.
    val exceedsUpperLimit: Boolean,
    val rawName: String,
    val amount: BigDecimal,
    val unit: String,
    val basisAmount: BigDecimal,
    val basisUnit: String,
)

fun Product.toCompareProduct(brand: BrandRef?) = CompareProduct(
    id = requireNotNull(id),
    name = name,
    manufacturer = manufacturer,
    brand = brand,
    exportOnly = exportOnly,
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
