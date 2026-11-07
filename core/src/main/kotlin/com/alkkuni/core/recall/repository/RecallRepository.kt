package com.alkkuni.core.recall.repository

import com.alkkuni.core.recall.entity.Recall
import org.springframework.data.jpa.repository.JpaRepository

interface RecallRepository : JpaRepository<Recall, Long> {
    // 제품 상세가 신고번호가 정확히 같은 회수만 읽는다(idx_recall_report_no). 이름 매칭은 회수 매칭(검수) 때 한다.
    // I0490에는 일반 식품 회수도 섞여 있어 유형도 맞춘다. 일반 식품의 번호가 우연히 같아도 건강기능식품에 붙지 않게 한다.
    fun findAllByReportNoAndProductTypeOrderById(reportNo: String, productType: String): List<Recall>
}
