package com.alkkuni.core.brand.repository

import com.alkkuni.core.brand.entity.Brand
import com.alkkuni.core.brand.entity.BrandAlias
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

// 브랜드와 공개 제품(삭제·원료 제외) 수. 국내용과 수출용(Product.exportOnly)을 나눠 센다.
data class BrandProductCount(val id: Long, val name: String, val domesticCount: Long, val exportCount: Long)

// 국내 제품 수. 응답(domesticCount)과 정렬 기준이 어긋나지 않게 한 곳에서 정한다.
private const val DOMESTIC_COUNT = "sum(CASE WHEN p.exportOnly = false THEN 1L ELSE 0L END)"

interface BrandRepository : JpaRepository<Brand, Long> {

    // 공개 제품이 하나도 없는 브랜드는 빠진다(골라도 결과가 없다). 국내 제품 수가 많은 순이고, 수출용만 있는 브랜드는 뒤다.
    // 애터미는 347개 중 245개가 수출용이라 전체 수로 세면 순위가 부풀려진다(전체 1위, 국내 6위).
    @Query(
        """
        SELECT new com.alkkuni.core.brand.repository.BrandProductCount(
            b.id, b.name,
            $DOMESTIC_COUNT,
            sum(CASE WHEN p.exportOnly = true THEN 1L ELSE 0L END))
        FROM Brand b JOIN Product p ON p.brandId = b.id
        WHERE p.status = com.alkkuni.core.product.enums.ProductStatus.ACTIVE AND p.rawMaterial = false
        GROUP BY b.id, b.name
        ORDER BY $DOMESTIC_COUNT DESC, count(p.id) DESC, b.name
        """,
    )
    fun findAllWithProductCount(): List<BrandProductCount>

    // 표기 키(BrandName.key)로 브랜드. 보류 표기(brand_id NULL)는 JOIN에서 빠져 null이다.
    @Query("SELECT b FROM Brand b JOIN BrandAlias a ON a.brandId = b.id WHERE a.aliasKey = :aliasKey")
    fun findByAliasKey(aliasKey: String): Brand?

    fun countByIdIn(ids: Collection<Long>): Long
}

interface BrandAliasRepository : JpaRepository<BrandAlias, String>
