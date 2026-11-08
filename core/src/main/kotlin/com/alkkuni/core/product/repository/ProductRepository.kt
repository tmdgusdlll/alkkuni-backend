package com.alkkuni.core.product.repository

import com.alkkuni.core.product.entity.Product
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository

// KotlinJdslJpqlExecutor: 성분별 제품 목록(ProductExploreRepository)의 동적 쿼리를 실행한다.
interface ProductRepository : JpaRepository<Product, Long>, KotlinJdslJpqlExecutor {
    // 수집 배치가 페이지(1000건) 단위로 기존 제품을 한 번에 읽는다.
    fun findAllByReportNoIn(reportNos: Collection<String>): List<Product>
}
