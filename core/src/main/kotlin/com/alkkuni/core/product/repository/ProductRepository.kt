package com.alkkuni.core.product.repository

import com.alkkuni.core.product.entity.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {
    // 수집 배치가 페이지(1000건) 단위로 기존 제품을 한 번에 읽는다.
    fun findAllByReportNoIn(reportNos: Collection<String>): List<Product>
}
