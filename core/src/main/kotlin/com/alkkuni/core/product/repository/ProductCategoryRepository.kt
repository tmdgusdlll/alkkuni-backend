package com.alkkuni.core.product.repository

import com.alkkuni.core.product.FunctionalCategory
import com.alkkuni.core.product.entity.ProductCategory
import com.alkkuni.core.product.entity.ProductCategoryId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

// 카테고리와 공개 제품(삭제·원료 제외) 수. 제품이 없는 카테고리는 행이 없다.
data class CategoryProductCount(val category: FunctionalCategory, val count: Long)

interface ProductCategoryRepository : JpaRepository<ProductCategory, ProductCategoryId> {

    @Query(
        """
        SELECT new com.alkkuni.core.product.repository.CategoryProductCount(c.category, count(c))
        FROM ProductCategory c JOIN Product p ON p.id = c.productId
        WHERE p.status = com.alkkuni.core.product.enums.ProductStatus.ACTIVE AND p.rawMaterial = false
        GROUP BY c.category
        """,
    )
    fun countProducts(): List<CategoryProductCount>

    // 한 페이지(또는 한 제품)의 카테고리를 한 번에 읽는다.
    fun findAllByProductIdIn(productIds: Collection<Long>): List<ProductCategory>
}
