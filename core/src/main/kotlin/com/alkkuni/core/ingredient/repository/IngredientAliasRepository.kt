package com.alkkuni.core.ingredient.repository

import com.alkkuni.core.ingredient.entity.IngredientAlias
import org.springframework.data.jpa.repository.JpaRepository

interface IngredientAliasRepository : JpaRepository<IngredientAlias, String>
