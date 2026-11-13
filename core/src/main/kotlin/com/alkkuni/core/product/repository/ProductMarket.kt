package com.alkkuni.core.product.repository

// 국내·수출 범위. ALL은 모두 보여주되 수출용(Product.exportOnly)을 뒤로 보낸다. 검색과 성분별 목록이 함께 쓴다.
enum class ProductMarket {
    ALL,
    DOMESTIC,
    EXPORT,
}
