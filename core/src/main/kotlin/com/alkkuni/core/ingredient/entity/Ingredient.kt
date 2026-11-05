package com.alkkuni.core.ingredient.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "ingredient")
class Ingredient(
    name: String,
    displayName: String = name,
    englishName: String? = null,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    // 화면에 보여줄 이름. 대부분 name과 같고, 약칭을 앞세울 때만 다르다(예: "총 HCA (하이드록시시트르산)").
    @Column(nullable = false)
    var displayName: String = displayName
        protected set

    // 영문 정식명. 원문·고시에 있는 성분만 채운다.
    var englishName: String? = englishName
        protected set

    var defaultUnit: String? = null
        protected set

    // IU → mg 환산 배율(1mg = iuPerMg IU). 성분마다 달라 사전에 둔다. 모르면 null(IU 함량의 1일 함량도 null).
    var iuPerMg: BigDecimal? = null
        protected set
}