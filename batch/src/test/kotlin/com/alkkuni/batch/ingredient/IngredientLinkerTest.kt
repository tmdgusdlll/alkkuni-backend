package com.alkkuni.batch.ingredient

import com.alkkuni.batch.product.I0030Row
import com.alkkuni.batch.product.ProductUpserter
import com.alkkuni.core.ingredient.repository.IngredientAliasRepository
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals

// 사전은 V7 초기 데이터를 그대로 쓴다.
@SpringBootTest(
    properties = [
        "spring.flyway.locations=filesystem:../api/src/main/resources/db/migration",
        "foodsafety.api-key=test-key",
    ],
)
@Testcontainers
@ActiveProfiles("test")
class IngredientLinkerTest(
    @Autowired private val upserter: ProductUpserter,
    @Autowired private val linker: IngredientLinker,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val aliasRepository: IngredientAliasRepository,
    @Autowired private val ingredientRepository: IngredientRepository,
    @Autowired private val productRepository: ProductRepository,
    @Autowired private val productIngredientRepository: ProductIngredientRepository,
    @Autowired private val tx: TransactionTemplate,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE product, product_ingredient")
    }

    // 사전이 생기기 전에 적재된 상태를 SQL로 만들고, 다시 연결하면 적재 때와 같은 결과가 되는지 본다.
    @Test
    fun `연결이 비어 있으면 다시 연결하고 IU 1일 함량도 다시 계산한다`() {
        upserter.upsert(listOf(row(STANDARD)))
        val afterLoad = snapshot()
        // 사전이 없을 때 적재된 상태: 연결이 없고 IU 행은 1일 함량을 계산하지 못했다
        jdbc.update("UPDATE product_ingredient SET ingredient_id = NULL")
        jdbc.update("UPDATE product_ingredient SET daily_amount = NULL, daily_unit = NULL WHERE unit = 'IU'")

        val result = linker.link()

        assertEquals(afterLoad, snapshot())
        assertEquals(2, result.relinkedRows) // 없는성분은 원래 NULL이라 바뀌지 않는다
        assertEquals(1, result.recalculatedIuRows)
        assertEquals(3L to 2L, result.targetRows to result.linkedRows)
        assertEquals(listOf("없는성분" to 1L), result.topUnlinked)
        // 비타민 D 400IU ÷ 40,000IU/mg = 0.01mg. 섭취방법이 없어 분모 2g을 하루치로 가정한다
        assertEquals(BigDecimal("0.01") to "mg", vitaminDDaily())
    }

    @Test
    fun `두 번 돌려도 결과가 같고 두 번째는 바꾸는 행이 없다`() {
        upserter.upsert(listOf(row(STANDARD)))
        linker.link()
        val first = snapshot()

        assertEquals(0, linker.link().relinkedRows)
        assertEquals(first, snapshot())
    }

    @Test
    fun `사전에서 빠진 키는 연결을 풀고 IU 1일 함량도 NULL로 돌린다`() {
        upserter.upsert(listOf(row(STANDARD)))
        jdbc.update("DELETE FROM ingredient_alias WHERE alias_key IN ('비타민C', '비타민D')")
        try {
            // 사전은 처음 읽을 때 메모리에 둔다. 테스트 컨텍스트의 사전은 이미 읽혔으므로 새로 만든다.
            val fresh = IngredientLinker(
                IngredientDictionary(aliasRepository, ingredientRepository),
                jdbc,
                productRepository,
                productIngredientRepository,
            )
            tx.execute { fresh.link() } // 직접 만든 객체라 @Transactional 프록시가 없다

            assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM product_ingredient WHERE ingredient_id IS NOT NULL", Int::class.java),
            )
            assertEquals(null, vitaminDDaily())
        } finally {
            jdbc.update(
                """
                INSERT INTO ingredient_alias VALUES
                    ('비타민C', (SELECT id FROM ingredient WHERE name = '비타민 C')),
                    ('비타민D', (SELECT id FROM ingredient WHERE name = '비타민 D'))
                """.trimIndent(),
            )
        }
    }

    private fun vitaminDDaily(): Pair<BigDecimal, String>? =
        jdbc.queryForObject("SELECT daily_amount, daily_unit FROM product_ingredient WHERE unit = 'IU'") { rs, _ ->
            rs.getBigDecimal(1)?.let { it.stripTrailingZeros() to rs.getString(2) }
        }

    private fun snapshot() = jdbc.queryForList(
        "SELECT raw_name, ingredient_id, daily_amount, daily_unit FROM product_ingredient ORDER BY id",
    )

    private fun row(standardText: String) = I0030Row(
        reportNo = "A",
        name = "제품",
        manufacturer = "제조사",
        intakeMethod = null,
        standardText = standardText,
        sourceUpdatedAt = LocalDateTime.of(2026, 11, 1, 0, 0),
    )

    private val STANDARD =
        "2.비타민D : 표시량(400IU/2g)의 80~180%\n3.비타민C : 표시량(100mg/2g)의 80~150%\n4.없는성분 : 표시량(1mg/2g)의 80~120%"
}
