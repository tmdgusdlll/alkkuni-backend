package com.alkkuni.core.product.entity

import com.alkkuni.core.product.FunctionalCategory
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 제품의 기능성 카테고리(V15). 쓰기는 batch의 CategoryLinker(JDBC)가 맡고, 엔티티는 조회(성분별 목록의 카테고리 필터·응답)에만 쓴다.
 * category는 enum 이름으로 읽어서 DB에 enum에 없는 값이 있으면 그 제품이 든 응답이 500이 된다.
 * FunctionalCategory에서 항목을 빼거나 이름을 바꿀 때는 batch(link-categories)를 새 규칙으로 먼저 돌려 옛 값을 지운 뒤 API를 배포한다.
 */
@Entity
@Table(name = "product_category")
@IdClass(ProductCategoryId::class)
class ProductCategory(
    @Id
    val productId: Long,

    @Id
    @Enumerated(EnumType.STRING)
    val category: FunctionalCategory,
)

// 복합 키. JPA가 기본 생성자와 equals·hashCode를 요구해 기본값을 둔 data class로 만든다(엔티티가 아니다).
data class ProductCategoryId(
    val productId: Long = 0,
    val category: FunctionalCategory = FunctionalCategory.EYE,
) : Serializable
