package com.alkkuni.core.ingredient.repository

import com.alkkuni.core.ingredient.entity.Ingredient
import org.springframework.data.jpa.repository.JpaRepository

interface IngredientRepository : JpaRepository<Ingredient, Long>
