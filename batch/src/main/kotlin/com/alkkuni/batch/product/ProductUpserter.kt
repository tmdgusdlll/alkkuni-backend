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
        // 한 페이지에 같은 신고번호가 두 번 오면 둘 다 새 제품으로 INSERT돼 UNIQUE 위반으로 페이지 전체가 실패한다.
        // 전체 데이터엔 중복이 없지만 한 줄로 막을 수 있어 뒤의 행을 남긴다.
        val uniqueRows = rows.associateBy { it.reportNo }.values
        val existing = productRepository.findAllByReportNoIn(uniqueRows.map { it.reportNo }).associateBy { it.reportNo }
        var changed = 0
        for (row in uniqueRows) {
            val found = existing[row.reportNo]
            // 원본도 같고 판정 규칙 버전도 같을 때만 건너뛴다.
            // 규칙을 바꿔 VERSION을 올리면 다시 적재하는 것만으로 재판정된다(검수한 제품은 applyParse가 지킨다).
            if (found?.sourceHash == row.sourceHash && found.parserVersion == IngredientAmountParser.VERSION) continue

            // 새 제품은 기준규격 없이 만들고, 기준규격과 판정(원료 여부 포함)은 applyParse가 채운다.
            val product = found?.apply {
                updateSource(row.name, row.manufacturer, row.intakeMethod, row.sourceHash, row.sourceUpdatedAt)
            } ?: Product(
                reportNo = row.reportNo,
                name = row.name,
                manufacturer = row.manufacturer,
                intakeMethod = row.intakeMethod,
                rawMaterial = false,
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
                        val daily = product.dailyAmount(it)
                        ProductIngredient(
                            productId = productId,
                            rawName = it.rawName,
                            amount = it.amount,
                            unit = it.unit,
                            basisAmount = it.basisAmount,
                            basisUnit = it.basisUnit,
                            extractionMethod = ExtractionMethod.RULE,
                            parserVersion = IngredientAmountParser.VERSION,
                            dailyAmount = daily?.first,
                            dailyUnit = daily?.second,
                        )
                    },
                )
            }
            changed++
        }
        return changed
    }
}
