package com.alkkuni.core.ingredient.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

// 성분명 키(IngredientName.normalize) → 대표 성분. 사전 데이터는 Flyway가 넣고 코드는 읽기만 한다.
@Entity
@Table(name = "ingredient_alias")
class IngredientAlias(
    @Id
    val aliasKey: String,

    @Column(nullable = false)
    val ingredientId: Long,
)
