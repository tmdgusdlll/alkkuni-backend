package com.alkkuni.core.product.entity

import com.alkkuni.core.product.enums.LinkStore
import com.alkkuni.core.product.enums.LinkType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 구매 링크(V19). 1차는 운영자가 SQL로 넣고 엔티티는 조회(제품 상세의 구매 버튼)에만 쓴다.
 * 제품과는 FK 값만 둔다(ProductIngredient와 같은 방식).
 */
@Entity
@Table(name = "shopping_link")
class ShoppingLink(
    @Column(nullable = false)
    val productId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val store: LinkStore,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: LinkType,
    @Column(nullable = false)
    val url: String,
    @Column(nullable = false)
    val checkedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
