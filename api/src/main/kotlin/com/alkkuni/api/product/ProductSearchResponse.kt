package com.alkkuni.api.product

import com.alkkuni.api.brand.BrandRef
import com.alkkuni.core.ingredient.entity.Ingredient
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.repository.ProductSearchRow
import java.math.BigDecimal
import java.time.LocalDate

data class ProductSearchResponse(
    val totalCount: Long,
    // 검색어가 성분 사전과 일치했을 때 그 성분. 함량순 정렬과 items의 dailyAmount가 이 성분 기준이다.
    val matchedIngredient: MatchedIngredient?,
    // 검색어가 브랜드 표기(brand_alias)와 일치했을 때 그 브랜드. 그 브랜드 제품이 결과에 더해진다. 화면은 브랜드 필터로 넘어가는 링크에 쓴다.
    val matchedBrand: BrandRef?,
    val items: List<ProductSearchItem>,
)

data class MatchedIngredient(
    val id: Long,
    val name: String,
)

data class ProductSearchItem(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    // 제품명에서 찾은 브랜드. 없으면 null(대부분)
    val brand: BrandRef?,
    val form: String?,
    val reportedOn: LocalDate?,
    // matchedIngredient의 1일 함량. 성분 검색이 아니거나 계산할 수 없으면 null.
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
    // dailyAmount가 하루치로 확인됐는지. DAILY·PER_SERVING만 "1일 기준", 나머지(UNVERIFIED·UNREADABLE 등)는 "표시 기준량"으로 보여준다.
    val basisCheck: BasisCheck?,
    // NEEDS_REVIEW면 함량이 이상해 검수 대기 중인 제품이다(예: 상한을 크게 넘는 값). 화면에서 경고로 구분한다.
    val parseStatus: ParseStatus,
    // dailyAmount가 matchedIngredient 상한의 2배를 넘으면 true(오류 의심, 비교·상세 API와 같은 판정). 성분 검색이 아니면 false.
    // parseStatus와 별개다. 상한 초과는 저장하지 않고 응답 때 계산한다(V11).
    val exceedsUpperLimit: Boolean,
)

fun Ingredient.toMatchedIngredient() = MatchedIngredient(id = requireNotNull(id), name = displayName)

fun ProductSearchRow.toItem(ingredient: Ingredient?, brand: BrandRef?) = ProductSearchItem(
    id = id,
    name = name,
    manufacturer = manufacturer,
    brand = brand,
    form = form,
    reportedOn = reportedOn,
    dailyAmount = dailyAmount,
    dailyUnit = dailyUnit,
    basisCheck = basisCheck,
    parseStatus = parseStatus,
    exceedsUpperLimit = ingredient?.exceedsUpperLimit(dailyAmount, dailyUnit) ?: false,
)
