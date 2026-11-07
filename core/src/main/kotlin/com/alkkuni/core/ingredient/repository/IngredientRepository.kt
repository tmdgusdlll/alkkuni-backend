package com.alkkuni.core.ingredient.repository

import com.alkkuni.core.ingredient.entity.Ingredient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface IngredientRepository : JpaRepository<Ingredient, Long> {

    // 성분명 키(IngredientName.normalize)로 대표 성분을 찾는다. alias → ingredient를 한 번의 조회로 가져온다.
    @Query("SELECT i FROM Ingredient i JOIN IngredientAlias a ON a.ingredientId = i.id WHERE a.aliasKey = :aliasKey")
    fun findByAliasKey(aliasKey: String): Ingredient?
}
