package com.alkkuni.api.brand

import com.alkkuni.api.common.ApiResponse
import com.alkkuni.api.common.PUBLIC_CACHE
import com.alkkuni.core.brand.repository.BrandProductCount
import com.alkkuni.core.brand.repository.BrandRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 브랜드 사전 전체(공개 제품이 있는 것만, 국내 제품 수 많은 순). 성분별 제품 목록의 브랜드 필터(brand=)에서 고르는 화면이 쓴다.
 * 200개 안팎이고 Flyway로만 바뀌어 페이지 없이 한 번에 내려준다. 성분 목록 API처럼 서비스 계층 없이 리포지토리를 바로 쓴다.
 */
@RestController
@RequestMapping("/api/brands")
class BrandController(private val brandRepository: BrandRepository) {

    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<BrandItem>>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(brandRepository.findAllWithProductCount().map { it.toItem() }))
}

data class BrandItem(
    val id: Long,
    val name: String,
    // 공개 제품 수(삭제·원료 제외)를 국내용·수출용으로 나눴다. 성분과 상관없는 전체 수다.
    // domesticCount가 0이면 수출용만 있는 브랜드라 market=domestic으로 고르면 결과가 없다.
    val domesticCount: Long,
    val exportCount: Long,
)

fun BrandProductCount.toItem() = BrandItem(id = id, name = name, domesticCount = domesticCount, exportCount = exportCount)
