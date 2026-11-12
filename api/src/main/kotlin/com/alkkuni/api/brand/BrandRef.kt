package com.alkkuni.api.brand

import com.alkkuni.core.brand.entity.Brand
import com.alkkuni.core.brand.repository.BrandRepository

// 제품 응답에 붙는 브랜드. 제품명에서 찾은 브랜드라 대부분(약 88%) 제품은 null이다. 제조사(manufacturer)와 다르다.
data class BrandRef(
    val id: Long,
    val name: String,
)

fun Brand.toRef() = BrandRef(id = requireNotNull(id), name = name)

// 제품 여러 개의 brandId → BrandRef. 한 번에 조회한다.
fun BrandRepository.refsOf(brandIds: Collection<Long?>): Map<Long, BrandRef> {
    val ids = brandIds.filterNotNull().toSet()
    return if (ids.isEmpty()) emptyMap() else findAllById(ids).associate { requireNotNull(it.id) to it.toRef() }
}
