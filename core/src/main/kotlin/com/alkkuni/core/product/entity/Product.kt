package com.alkkuni.core.product.entity

import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.enums.ProductStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "product")
class Product(
    @Column(nullable = false, unique = true)
    val reportNo: String,
    name: String,
    manufacturer: String? = null,
    intakeMethod: String? = null,
    standardText: String? = null,
    rawMaterial: Boolean,
    sourceHash: String? = null,
    sourceUpdatedAt: LocalDateTime? = null,
    parseStatus: ParseStatus,
    parseIssue: String? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    var manufacturer: String? = manufacturer
        protected set

    var intakeMethod: String? = intakeMethod
        protected set

    var standardText: String? = standardText
        protected set

    @Column(nullable = false)
    var rawMaterial: Boolean = rawMaterial
        protected set

    var sourceHash: String? = sourceHash
        protected set

    var sourceUpdatedAt: LocalDateTime? = sourceUpdatedAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = ProductStatus.ACTIVE
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var parseStatus: ParseStatus = parseStatus
        protected set

    var parseIssue: String? = parseIssue
        protected set
}