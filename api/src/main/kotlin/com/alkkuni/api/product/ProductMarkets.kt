package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.common.ErrorDetail
import com.alkkuni.core.product.repository.ProductMarket

// API 값은 소문자(all, domestic, export)다. 검색과 성분별 목록이 함께 쓴다.
fun parseMarket(market: String): ProductMarket = ProductMarket.entries.find { it.name.lowercase() == market }
    ?: throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail("market", "all, domestic, export 중 하나여야 합니다.")))
