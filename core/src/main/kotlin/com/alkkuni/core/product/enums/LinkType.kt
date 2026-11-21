package com.alkkuni.core.product.enums

// DETAIL: 상품 상세 링크. SEARCH: "제조사 + 제품명" 검색 결과 링크(확신도가 낮거나 상세 링크가 깨졌을 때, ADR-008).
// 대표 링크를 고를 때 선언 순서(DETAIL 먼저)를 쓴다.
enum class LinkType {
    DETAIL,
    SEARCH
}
