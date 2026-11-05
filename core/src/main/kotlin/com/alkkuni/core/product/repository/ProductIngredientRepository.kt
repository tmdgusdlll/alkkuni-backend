package com.alkkuni.core.product.repository

import com.alkkuni.core.product.entity.ProductIngredient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductIngredientRepository : JpaRepository<ProductIngredient, Long> {
    // 파생 deleteBy는 행마다 읽고 지운다. 재파싱은 제품의 성분 행을 통째로 바꾸므로 쿼리 한 번으로 지운다.
    @Modifying
    @Query("delete from ProductIngredient pi where pi.productId = :productId")
    fun deleteAllByProductId(productId: Long): Int

    // link-ingredients가 IU 행의 1일 함량을 다시 계산할 때 쓴다(로컬 21행).
    fun findAllByUnitIn(units: Collection<String>): List<ProductIngredient>
}
