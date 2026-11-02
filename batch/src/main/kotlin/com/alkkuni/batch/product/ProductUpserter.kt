package com.alkkuni.batch.product

import com.alkkuni.core.parser.IngredientAmountParser
import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.entity.ProductIngredient
import com.alkkuni.core.product.enums.ExtractionMethod
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * I0030 행을 제품·성분 함량으로 멱등 저장한다. 같은 행을 몇 번 넣어도 결과가 같다.
 * - 제품은 신고번호로 찾아 갱신하고, 없으면 만든다.
 * - 성분 행은 자연키가 없어(같은 성분명이 두 번 나올 수 있음) 제품 단위로 지우고 다시 넣는다.
 */
@Service
class ProductUpserter(
    private val productRepository: ProductRepository,
    private val productIngredientRepository: ProductIngredientRepository,
) {
    /** 한 페이지를 한 트랜잭션으로 저장한다. 바뀌어서 다시 쓴 제품 수를 돌려준다. */
    @Transactional
    fun upsert(rows: List<I0030Row>): Int {
        val existing = productRepository.findAllByReportNoIn(rows.map { it.reportNo }).associateBy { it.reportNo }
        var changed = 0
        for (row in rows) {
            val found = existing[row.reportNo]
            if (found?.sourceHash == row.sourceHash) continue

            val rawMaterial = row.standardText?.let(IngredientAmountParser::isRawMaterialOnly) ?: false
            // 새 제품은 기준규격 없이 만들고, 기준규격과 판정은 applyParse가 채운다.
            val product = found?.apply {
                updateSource(row.name, row.manufacturer, row.intakeMethod, rawMaterial, row.sourceHash, row.sourceUpdatedAt)
            } ?: Product(
                reportNo = row.reportNo,
                name = row.name,
                manufacturer = row.manufacturer,
                intakeMethod = row.intakeMethod,
                rawMaterial = rawMaterial,
                sourceHash = row.sourceHash,
                sourceUpdatedAt = row.sourceUpdatedAt,
                parseStatus = ParseStatus.NO_AMOUNT,
                parserVersion = IngredientAmountParser.VERSION,
            )
            val result = IngredientAmountParser.parse(row.standardText ?: "")
            val reparsed = product.applyParse(row.standardText, result)
            val productId = productRepository.save(product).id!!

            if (reparsed) {
                if (found != null) productIngredientRepository.deleteAllByProductId(productId)
                productIngredientRepository.saveAll(
                    result.amounts.map {
                        ProductIngredient(
                            productId = productId,
                            rawName = it.rawName,
                            amount = it.amount,
                            unit = it.unit,
                            basisAmount = it.basisAmount,
                            basisUnit = it.basisUnit,
                            extractionMethod = ExtractionMethod.RULE,
                            parserVersion = IngredientAmountParser.VERSION,
                        )
                    },
                )
            }
            changed++
        }
        return changed
    }
}
