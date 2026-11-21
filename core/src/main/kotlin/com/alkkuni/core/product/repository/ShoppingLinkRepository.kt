package com.alkkuni.core.product.repository

import com.alkkuni.core.product.entity.ShoppingLink
import org.springframework.data.jpa.repository.JpaRepository

interface ShoppingLinkRepository : JpaRepository<ShoppingLink, Long> {

    fun findAllByProductId(productId: Long): List<ShoppingLink>
}
