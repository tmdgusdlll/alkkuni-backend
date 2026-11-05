package com.alkkuni.batch.ingredient

import com.alkkuni.core.ingredient.IngredientName
import com.alkkuni.core.ingredient.entity.Ingredient
import com.alkkuni.core.ingredient.repository.IngredientAliasRepository
import com.alkkuni.core.ingredient.repository.IngredientRepository
import org.springframework.stereotype.Component

/**
 * 성분명 원문 → 대표 성분. 사전(수백 행)을 처음 쓸 때 한 번 읽어 메모리에 둔다.
 * 배치는 한 번 돌고 끝나므로 실행 중 사전이 바뀌는 경우는 없다. 사전을 고친 뒤에는 다음 실행부터 반영된다.
 */
@Component
class IngredientDictionary(
    private val aliasRepository: IngredientAliasRepository,
    private val ingredientRepository: IngredientRepository,
) {
    private val byKey: Map<String, Ingredient> by lazy {
        val ingredients = ingredientRepository.findAll().associateBy { it.id!! }
        aliasRepository.findAll().associate { it.aliasKey to ingredients.getValue(it.ingredientId) }
    }

    fun find(rawName: String): Ingredient? = byKey[IngredientName.normalize(rawName)]
}
