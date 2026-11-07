package com.alkkuni.api.product

import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.entity.ProductIngredient
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.recall.entity.Recall
import com.alkkuni.core.recall.enums.RecallListStatus
import java.math.BigDecimal
import java.time.LocalDate

data class ProductDetailResponse(
    val id: Long,
    val reportNo: String,
    val name: String,
    val manufacturer: String?,
    val form: String?,
    val reportedOn: LocalDate?,
    val intakeMethod: String?,
    val functionalMaterials: String?,
    val functionality: String?,
    val precautions: String?,
    // DAILY·PER_SERVING만 "1일 기준", 그 외는 "표시 기준량" 배지(검색·비교 API와 같은 규칙)
    val basisCheck: BasisCheck?,
    // NEEDS_REVIEW면 화면이 검수 대기 경고를 단다
    val parseStatus: ParseStatus,
    // 성분표 순서. 같은 성분이 여러 행이어도 합치지 않는다(비교 API와 같은 이유).
    val ingredients: List<DetailIngredient>,
    // 신고번호가 정확히 같은 회수만. 신고번호가 없거나 다른 회수는 회수 매칭(검수) 뒤에 붙는다.
    val recalls: List<DetailRecall>,
)

data class DetailIngredient(
    // 사전에 없는 성분은 null이고 name은 원문 이름이다.
    val ingredientId: Long?,
    val name: String,
    val rawName: String,
    // 1일 함량(단위 통일). 계산할 수 없으면 null이고 화면은 원문 값(amount/basis)을 보여준다.
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
    val amount: BigDecimal,
    val unit: String,
    val basisAmount: BigDecimal,
    val basisUnit: String,
)

data class DetailRecall(
    val productName: String,
    val manufacturer: String?,
    val reason: String?,
    val expiryText: String?,
    // DELISTED: 식약처 목록에서 빠졌지만 회수 사실은 남긴다.
    val listStatus: RecallListStatus,
)

fun Product.toDetailResponse(ingredients: List<DetailIngredient>, recalls: List<DetailRecall>) = ProductDetailResponse(
    id = requireNotNull(id),
    reportNo = reportNo,
    name = name,
    manufacturer = manufacturer,
    form = form,
    reportedOn = reportedOn,
    intakeMethod = intakeMethod,
    functionalMaterials = functionalMaterials,
    functionality = functionality,
    precautions = precautions,
    basisCheck = basisCheck,
    parseStatus = parseStatus,
    ingredients = ingredients,
    recalls = recalls,
)

fun ProductIngredient.toDetailIngredient(name: String?) = DetailIngredient(
    ingredientId = ingredientId,
    name = name ?: rawName,
    rawName = rawName,
    dailyAmount = dailyAmount,
    dailyUnit = dailyUnit,
    amount = amount,
    unit = unit,
    basisAmount = basisAmount,
    basisUnit = basisUnit,
)

fun Recall.toDetailRecall() = DetailRecall(
    productName = productName,
    manufacturer = manufacturer,
    reason = reason,
    expiryText = expiryText,
    listStatus = listStatus,
)
