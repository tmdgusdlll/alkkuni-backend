package com.alkkuni.api.product

import com.alkkuni.api.brand.BrandRef
import com.alkkuni.core.ingredient.entity.Ingredient
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.repository.ProductExploreRow
import java.math.BigDecimal
import java.time.LocalDate

data class ProductExploreResponse(
    val totalCount: Long,
    // 고른 성분(요청 순서). items[].amounts가 같은 순서다.
    val ingredients: List<ExploreIngredient>,
    // 함량순 정렬과 함량 범위가 걸린 성분
    val baseIngredientId: Long,
    val items: List<ProductExploreItem>,
)

data class ExploreIngredient(
    val id: Long,
    val name: String,
    // 주 단위. 함량의 dailyUnit이 이 값과 다르면 "다른 단위 표기"라 주 단위 값과 비교할 수 없다. null이면 단위가 섞이지 않은 성분이다.
    val defaultUnit: String?,
    // 1일 기준값과 단위(성분 목록 API와 같음). 기준값이 없는 성분이나 사전에 없는 성분은 null이다.
    val referenceAmount: BigDecimal?,
    val referenceUnit: String?,
)

data class ProductExploreItem(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    // 제품명에서 찾은 브랜드. 없으면 null(대부분)
    val brand: BrandRef?,
    // 수출용 제품(제품명 표기로 판정). 화면은 "수출용" 배지를 단다. 기본 정렬에서 국내 제품 뒤에 온다.
    val exportOnly: Boolean,
    val form: String?,
    val reportedOn: LocalDate?,
    // 함량이 하루치로 확인됐는지. DAILY·PER_SERVING만 "1일 기준", 나머지는 "표시 기준량"(검색 API와 같음).
    val basisCheck: BasisCheck?,
    val parseStatus: ParseStatus,
    val amounts: List<ExploreItemAmount>,
)

// 고른 성분 하나의 함량 행들(원문 성분표 순서, 비교 API와 같은 타입).
// 한 제품에 같은 성분 행이 여러 개면 합치지 않는다(PLANNING.md "함량 표시 규칙"). 1일 함량을 모르면 dailyAmount가 null이고 원문 값(amount/basis)을 보여준다.
data class ExploreItemAmount(
    val ingredientId: Long,
    val values: List<CompareAmount>,
)

fun Ingredient.toExploreIngredient() = ExploreIngredient(
    id = requireNotNull(id),
    name = displayName,
    defaultUnit = defaultUnit,
    referenceAmount = referenceAmount,
    referenceUnit = referenceUnit,
)

fun ProductExploreRow.toExploreItem(brand: BrandRef?, amounts: List<ExploreItemAmount>) = ProductExploreItem(
    id = id,
    name = name,
    manufacturer = manufacturer,
    brand = brand,
    exportOnly = exportOnly,
    form = form,
    reportedOn = reportedOn,
    basisCheck = basisCheck,
    parseStatus = parseStatus,
    amounts = amounts,
)
