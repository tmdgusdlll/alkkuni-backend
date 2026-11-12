package com.alkkuni.core.brand.repository

import com.alkkuni.core.brand.entity.Brand
import com.alkkuni.core.brand.entity.BrandAlias
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

// 브랜드와 공개 제품(삭제·원료 제외) 수
data class BrandProductCount(val id: Long, val name: String, val productCount: Long)

interface BrandRepository : JpaRepository<Brand, Long> {

    // 공개 제품이 하나도 없는 브랜드는 빠진다(골라도 결과가 없다). V13 부분 인덱스(brand_id)를 쓴다.
    @Query(
        """
        SELECT new com.alkkuni.core.brand.repository.BrandProductCount(b.id, b.name, count(p.id))
        FROM Brand b JOIN Product p ON p.brandId = b.id
        WHERE p.status = com.alkkuni.core.product.enums.ProductStatus.ACTIVE AND p.rawMaterial = false
        GROUP BY b.id, b.name
        ORDER BY count(p.id) DESC, b.name
        """,
    )
    fun findAllWithProductCount(): List<BrandProductCount>
}

interface BrandAliasRepository : JpaRepository<BrandAlias, String>
