package com.alkkuni.core.brand.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

// 대표 브랜드. 사전 데이터는 Flyway(V13)가 넣고 코드는 읽기만 한다.
@Entity
@Table(name = "brand")
class Brand(
    @Column(nullable = false, unique = true)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

// 표기 키(BrandName.key) → 브랜드. brandId가 null이면 검토에서 보류한 표기다(BrandMatcher).
@Entity
@Table(name = "brand_alias")
class BrandAlias(
    @Id
    val aliasKey: String,

    val brandId: Long?,

    @Column(nullable = false)
    val wholeWord: Boolean,
)
