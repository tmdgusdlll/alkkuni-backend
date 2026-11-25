package com.alkkuni.batch.brand

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals

// 사전은 V13 초기 데이터를 그대로 쓴다.
@SpringBootTest(
    properties = [
        "spring.flyway.locations=filesystem:../api/src/main/resources/db/migration",
        "foodsafety.api-key=test-key",
    ],
)
@Testcontainers
@ActiveProfiles("test")
class BrandLinkerTest(
    @Autowired private val linker: BrandLinker,
    @Autowired private val jdbc: JdbcTemplate,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE product, product_ingredient, product_category, shopping_link")
    }

    @Test
    fun `제품명으로 브랜드를 다시 연결하고 몇 번 돌려도 같다`() {
        val atomy = product("1", "ATOMY Probiotics")
        val jbio = product("2", "종근당(鍾根堂)바이오 발효홍삼유산균 골드")
        val ckd = product("3", "종근당 비타민C 1000mg")
        val none = product("4", "고려홍삼농축액플러스")
        // 사전에서 빠진 브랜드를 가리키던 제품은 NULL로 돌아간다
        jdbc.update("UPDATE product SET brand_id = (SELECT id FROM brand WHERE name = '애터미') WHERE id = ?", none)

        val first = linker.link()

        assertEquals("애터미", brandOf(atomy))
        assertEquals("종근당바이오", brandOf(jbio))
        assertEquals("종근당", brandOf(ckd))
        assertEquals(null, brandOf(none))
        assertEquals(4, first.relinked)
        assertEquals(3, first.linked)
        assertEquals(0, linker.link().relinked)
    }

    @Test
    fun `보류한 표기는 사전에 없다`() {
        // 한미는 한미약품·한미양행이 섞여 보류했다(docs/brand-dictionary.csv)
        val hanmi = product("1", "한미감마리놀렌산")
        // 유한M은 보류했다. "유한"(유한양행)으로 내려가지 않는다
        val yuhanM = product("2", "유한m비타민D2000IU")
        val yuhan = product("3", "유한 비타민C")
        linker.link()
        assertEquals(null, brandOf(hanmi))
        assertEquals(null, brandOf(yuhanM))
        assertEquals("유한양행", brandOf(yuhan))
    }

    @Test
    fun `회사 브랜드는 정식명으로 보이고 웅진은 웅진식품과 오직으로 나뉜다`() {
        // V24. 줄임말 표기("광동")는 별칭으로 남아 그대로 연결된다
        val kwangdong = product("1", "광동 관절팩트")
        val ilyang = product("2", "[일양약품] 아연365")
        val ildong = product("3", "일동 맥스 솔루션")
        // 웅진 접두어에 웅진식품(알로에겔)과 웅진헬스원(오직)이 섞여 있었다. "웅진오직"이 "웅진"보다 길어 먼저 맞는다
        val aloe = product("4", "웅진 알로에겔 플러스 프리미엄")
        val ogic = product("5", "웅진 오직 뼈건강")
        val ogicAlone = product("6", "오직 관절")
        val ogicColon = product("7", "오직:프로바이오틱스")
        // 판매사를 확인하지 못해 보류했다
        val kidsRedGinseng = product("8", "웅진 어린이 홍삼")
        // "오직"은 흔한 낱말이라 첫 단어일 때만 맞는다
        val otherOgic = product("9", "오직홍삼 골드")
        linker.link()
        assertEquals("광동제약", brandOf(kwangdong))
        assertEquals("일양약품", brandOf(ilyang))
        assertEquals("일동제약", brandOf(ildong))
        assertEquals("웅진식품", brandOf(aloe))
        assertEquals("오직", brandOf(ogic))
        assertEquals("오직", brandOf(ogicAlone))
        assertEquals("오직", brandOf(ogicColon))
        assertEquals(null, brandOf(kidsRedGinseng))
        assertEquals(null, brandOf(otherOgic))
    }

    private fun product(reportNo: String, name: String): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, parse_status, parser_version, status)
        VALUES (?, ?, false, 'NO_AMOUNT', 'test', 'ACTIVE') RETURNING id
        """.trimIndent(),
        Long::class.java, reportNo, name,
    )!!

    private fun brandOf(productId: Long): String? = jdbc.queryForList(
        "SELECT b.name FROM product p JOIN brand b ON b.id = p.brand_id WHERE p.id = ?",
        String::class.java, productId,
    ).firstOrNull()
}
