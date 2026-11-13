package com.alkkuni.batch.product

import com.alkkuni.core.ingestion.entity.IngestionCursor
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import com.alkkuni.core.parser.IngredientAmountParser
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
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest(
    properties = [
        // 스키마는 api가 가진 마이그레이션 파일로 만든다. 테스트 작업 디렉터리는 batch 모듈이다.
        "spring.flyway.locations=filesystem:../api/src/main/resources/db/migration",
        "foodsafety.api-key=test-key",
    ],
)
@Testcontainers
@ActiveProfiles("test")
class ProductUpserterTest(
    @Autowired private val upserter: ProductUpserter,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val cursorRepository: IngestionCursorRepository,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE product, product_ingredient, product_category, ingestion_cursor")
    }

    // 잡은 트랜잭션 밖에서 커서를 읽고(advance) save한다. 분리된 객체의 변경이 DB에 반영되는지 확인한다.
    @Test
    fun `읽은 커서를 advance 후 저장하면 날짜가 바뀐다`() {
        cursorRepository.save(IngestionCursor("I0030", LocalDate.of(2026, 11, 1), LocalDateTime.now()))
        val cursor = cursorRepository.findById("I0030").get()

        cursor.advance(LocalDate.of(2026, 11, 2), LocalDateTime.now())
        cursorRepository.save(cursor)

        assertEquals(1, cursorRepository.count())
        assertEquals(LocalDate.of(2026, 11, 2), cursorRepository.findById("I0030").get().lastCollectedDate)
    }

    @Test
    fun `같은 행을 두 번 넣어도 제품·성분 행 수가 같다`() {
        val rows = listOf(row("A", VITAMIN), row("B", "1. 성상 : 분말"))

        assertEquals(2, upserter.upsert(rows))
        val first = counts()
        assertEquals(0, upserter.upsert(rows))

        assertEquals(first, counts())
        assertEquals(2 to 2, first)
        // 함량이 없어 성분 행이 없는 B도 판정 버전이 남는다(재판정 대상을 찾을 수 있다)
        assertEquals(
            IngredientAmountParser.VERSION,
            jdbc.queryForObject("SELECT parser_version FROM product WHERE report_no = 'B'", String::class.java),
        )
    }

    @Test
    fun `제형·신고일·기능성·주의사항 값을 저장하고 제형만 바뀌어도 갱신한다`() {
        upserter.upsert(listOf(row("A", VITAMIN, form = "캡슐")))
        assertEquals(1, upserter.upsert(listOf(row("A", VITAMIN, form = "정"))))

        val saved = jdbc.queryForMap(
            "SELECT form, reported_on, functional_materials, functionality, precautions FROM product WHERE report_no = 'A'",
        )
        assertEquals("정", saved["form"])
        assertEquals(java.sql.Date.valueOf("2020-01-15"), saved["reported_on"])
        assertEquals("감마리놀렌산 함유 유지", saved["functional_materials"])
        assertEquals("혈중 콜레스테롤 개선", saved["functionality"])
        assertEquals("임산부는 섭취에 주의", saved["precautions"])
    }

    @Test
    fun `기준규격이 바뀌면 성분 행을 지우고 새로 넣는다`() {
        upserter.upsert(listOf(row("A", VITAMIN)))
        upserter.upsert(listOf(row("A", "2. 비타민C : 표시량(100mg/1g)")))

        assertEquals(1 to 1, counts())
        assertEquals("비타민C", jdbc.queryForObject("SELECT raw_name FROM product_ingredient", String::class.java))
    }

    @Test
    fun `검수한 제품은 원문이 같으면 다른 값만 갱신하고 성분 행은 그대로 둔다`() {
        upserter.upsert(listOf(row("A", VITAMIN)))
        jdbc.update("UPDATE product SET parse_status = 'REVIEWED'")

        upserter.upsert(listOf(row("A", VITAMIN, name = "새 이름")))

        assertEquals("REVIEWED", jdbc.queryForObject("SELECT parse_status FROM product", String::class.java))
        assertEquals("새 이름", jdbc.queryForObject("SELECT name FROM product", String::class.java))
        assertEquals(1 to 2, counts())
    }

    @Test
    fun `원본이 같아도 판정 버전이 옛 것이면 다시 판정한다`() {
        upserter.upsert(listOf(row("A", RAW_ONLY)))
        // 옛 규칙으로 판정된 상태를 만든다: 버전이 다르고 원료 여부가 틀려 있다
        jdbc.update("UPDATE product SET parser_version = '0', raw_material = false")

        assertEquals(1, upserter.upsert(listOf(row("A", RAW_ONLY))))

        assertEquals(true, jdbc.queryForObject("SELECT raw_material FROM product", Boolean::class.java))
        assertEquals(
            IngredientAmountParser.VERSION,
            jdbc.queryForObject("SELECT parser_version FROM product", String::class.java),
        )
        // 같은 버전이 되면 다시 건너뛴다
        assertEquals(0, upserter.upsert(listOf(row("A", RAW_ONLY))))
    }

    @Test
    fun `한 페이지에 같은 신고번호가 두 번 와도 제품은 하나다`() {
        upserter.upsert(listOf(row("A", VITAMIN), row("A", VITAMIN, name = "뒤의 행")))

        assertEquals("뒤의 행", jdbc.queryForObject("SELECT name FROM product", String::class.java))
        assertEquals(1 to 2, counts())
    }

    // 사전은 V7 초기 데이터다. 성분명 표기 변형도 정리 규칙(IngredientName)을 거쳐 같은 대표 성분으로 연결된다.
    @Test
    fun `적재할 때 성분 사전으로 대표 성분을 연결한다`() {
        upserter.upsert(listOf(row("A", VITAMIN + "\n4.셀렌(%) : 표시량(30㎍/2g)의 80~150%\n5.없는성분 : 표시량(1mg/2g)의 80~120%")))

        val linked = jdbc.query(
            "SELECT pi.raw_name, i.name FROM product_ingredient pi LEFT JOIN ingredient i ON i.id = pi.ingredient_id ORDER BY pi.id",
        ) { rs, _ -> rs.getString(1) to rs.getString(2) }
        assertEquals(
            listOf("감마리놀렌산" to "감마리놀렌산", "비타민E" to "비타민 E", "셀렌(%)" to "셀레늄", "없는성분" to null),
            linked,
        )
    }

    // 브랜드 사전은 V13 초기 데이터를 쓴다(TRUNCATE 대상이 아니다)
    @Test
    fun `적재할 때 제품명으로 브랜드를 연결하고 이름이 바뀌면 다시 연결한다`() {
        upserter.upsert(listOf(row("A", VITAMIN, name = "애터미 비타민C")))
        assertEquals("애터미", brandOf("A"))

        upserter.upsert(listOf(row("A", VITAMIN, name = "종근당(鍾根堂)바이오 비타민C")))
        assertEquals("종근당바이오", brandOf("A"))
    }

    @Test
    fun `적재할 때 기능성 원문으로 카테고리를 쓰고 원문이 바뀌면 다시 쓴다`() {
        upserter.upsert(listOf(row("A", VITAMIN)))
        assertEquals(listOf("CHOLESTEROL"), categoriesOf("A"))

        upserter.upsert(listOf(row("A", VITAMIN, functionality = "①눈 건강에 도움을 줄 수 있음 ②간 건강에 도움을 줄 수 있음")))
        assertEquals(listOf("EYE", "LIVER"), categoriesOf("A"))
    }

    private fun categoriesOf(reportNo: String) = jdbc.queryForList(
        "SELECT c.category FROM product_category c JOIN product p ON p.id = c.product_id WHERE p.report_no = ? ORDER BY 1",
        String::class.java,
        reportNo,
    )

    private fun brandOf(reportNo: String) = jdbc.queryForObject(
        "SELECT b.name FROM product p LEFT JOIN brand b ON b.id = p.brand_id WHERE p.report_no = ?",
        String::class.java,
        reportNo,
    )

    private fun counts() =
        jdbc.queryForObject("SELECT count(*) FROM product", Int::class.java)!! to
            jdbc.queryForObject("SELECT count(*) FROM product_ingredient", Int::class.java)!!

    private fun row(
        reportNo: String,
        standardText: String,
        name: String = "제품",
        form: String? = "캡슐",
        functionality: String = "혈중 콜레스테롤 개선",
    ) = I0030Row(
        reportNo = reportNo,
        name = name,
        manufacturer = "제조사",
        intakeMethod = null,
        standardText = standardText,
        sourceUpdatedAt = LocalDateTime.of(2026, 11, 1, 0, 0),
        form = form,
        reportedOn = LocalDate.of(2020, 1, 15),
        functionalMaterials = "감마리놀렌산 함유 유지",
        functionality = functionality,
        precautions = "임산부는 섭취에 주의",
    )

    private val RAW_ONLY = "2. 진세노사이드 Rg1+Rb1+Rg3의 합 *원료성제품 표시량(30mg/g)의 100% 이상"
    private val VITAMIN = "2.감마리놀렌산 : 표시량(240mg/2g)의 80~120%\n3.비타민E : 표시량(17mg/2g)의 80~150%"
}
