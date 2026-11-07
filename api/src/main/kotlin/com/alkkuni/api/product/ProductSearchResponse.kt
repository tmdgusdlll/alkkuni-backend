package com.alkkuni.api.product

import com.alkkuni.core.ingredient.entity.Ingredient
import com.alkkuni.core.product.repository.ProductSearchRow
import java.math.BigDecimal
import java.time.LocalDate

data class ProductSearchResponse(
    val totalCount: Long,
    // 검색어가 성분 사전과 일치했을 때 그 성분. 함량순 정렬과 items의 dailyAmount가 이 성분 기준이다.
    val matchedIngredient: MatchedIngredient?,
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
    val form: String?,
    val reportedOn: LocalDate?,
    // matchedIngredient의 1일 함량. 성분 검색이 아니거나 계산할 수 없으면 null.
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
)

fun Ingredient.toMatchedIngredient() = MatchedIngredient(id = requireNotNull(id), name = displayName)

fun ProductSearchRow.toItem() = ProductSearchItem(
    id = id,
    name = name,
    manufacturer = manufacturer,
    form = form,
    reportedOn = reportedOn,
    dailyAmount = dailyAmount,
    dailyUnit = dailyUnit,
)
