package com.alkkuni.core.product.entity

import com.alkkuni.core.product.enums.ExtractionMethod
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "product_ingredient")
class ProductIngredient(

    @Column(nullable = false)
    val productId: Long,
    rawName: String,
    amount: BigDecimal,
    unit: String,
    basisAmount: BigDecimal,
    basisUnit: String,
    extractionMethod: ExtractionMethod,
    parserVersion: String,
    dailyAmount: BigDecimal?,
    dailyUnit: String?,
) {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    var ingredientId: Long? = null
        protected set

    @Column(nullable = false)
    var rawName: String = rawName
        protected set

    @Column(nullable = false)
    var amount: BigDecimal = amount
        protected set

    @Column(nullable = false)
    var unit: String = unit
        protected set

    @Column(nullable = false)
    var basisAmount: BigDecimal = basisAmount
        protected set

    @Column(nullable = false)
    var basisUnit: String = basisUnit
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var extractionMethod: ExtractionMethod = extractionMethod
        protected set

    @Column(nullable = false)
    var parserVersion: String = parserVersion
        protected set

    // 1일 함량. 단위는 AmountUnit로 통일(mg, mg α-TE, CFU 등). 계산할 수 없으면 null(Product.dailyAmount).
    var dailyAmount: BigDecimal? = dailyAmount
        protected set

    var dailyUnit: String? = dailyUnit
        protected set
}