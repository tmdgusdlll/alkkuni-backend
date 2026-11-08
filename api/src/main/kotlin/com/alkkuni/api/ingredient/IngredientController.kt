package com.alkkuni.api.ingredient

import com.alkkuni.api.common.ApiResponse
import com.alkkuni.api.common.PUBLIC_CACHE
import com.alkkuni.core.ingredient.entity.Ingredient
import com.alkkuni.core.ingredient.repository.IngredientRepository
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 성분 사전 전체. 성분별 제품 목록에서 성분을 고르는 화면이 쓴다.
 * 215개뿐이고 Flyway로만 바뀌어 페이지 없이 한 번에 내려준다. 서비스 계층 없이 리포지토리를 바로 쓴다.
 */
@RestController
@RequestMapping("/api/ingredients")
class IngredientController(private val ingredientRepository: IngredientRepository) {

    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<IngredientItem>>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(ingredientRepository.findAll(Sort.by("displayName")).map { it.toItem() }))
}

data class IngredientItem(
    val id: Long,
    val name: String,
    val englishName: String?,
    // 성분별 제품 목록의 함량 범위(min·max) 단위. null이면 단위가 섞이지 않은 성분이다.
    val defaultUnit: String?,
)

fun Ingredient.toItem() = IngredientItem(id = requireNotNull(id), name = displayName, englishName = englishName, defaultUnit = defaultUnit)
