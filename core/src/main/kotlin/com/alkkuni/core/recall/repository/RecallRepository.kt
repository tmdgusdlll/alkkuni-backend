package com.alkkuni.core.recall.repository

import com.alkkuni.core.recall.entity.Recall
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RecallRepository : JpaRepository<Recall, Long> {
    // 제품 상세가 신고번호가 정확히 같은 회수만 읽는다(idx_recall_report_no). 이름 매칭은 회수 매칭(검수) 때 한다.
    // I0490에는 일반 식품 회수도 섞여 있어 유형도 맞춘다. 일반 식품의 번호가 우연히 같아도 건강기능식품에 붙지 않게 한다.
    fun findAllByReportNoAndProductTypeOrderById(reportNo: String, productType: String): List<Recall>

    // 소식 목록. DELISTED도 회수 사실은 그대로라 남긴다. 등록일이 없는 행(배포 전에 이미 빠진 건)은 뒤로 보낸다.
    // ponytail: 건강기능식품 회수가 수십 건이라 인덱스 없이 정렬한다. 수천 건이 되면 (product_type, registered_at) 인덱스
    @Query("SELECT r FROM Recall r WHERE r.productType = :productType ORDER BY r.registeredAt DESC NULLS LAST, r.id DESC")
    fun findNews(productType: String, pageable: Pageable): Page<Recall>
}
