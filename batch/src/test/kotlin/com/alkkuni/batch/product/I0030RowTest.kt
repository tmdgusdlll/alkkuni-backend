package com.alkkuni.batch.product

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class I0030RowTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `새 필드를 읽고 빈 값은 null로 바꾼다`() {
        val row = I0030Row.from(
            mapper.readTree(
                """
                {"PRDLST_REPORT_NO": "2020001", "PRDLST_NM": "제품", "BSSH_NM": "제조사",
                 "NTK_MTHD": "", "STDR_STND": "", "LAST_UPDT_DTM": "20261101",
                 "PRDT_SHAP_CD_NM": "캡슐", "PRMS_DT": "20200115",
                 "INDIV_RAWMTRL_NM": "홍삼농축액(고시형), 비타민C", "PRIMARY_FNCLTY": " 면역력 증진 ",
                 "IFTKN_ATNT_MATR_CN": ""}
                """,
            ),
        )

        assertEquals("캡슐", row.form)
        assertEquals(LocalDate.of(2020, 1, 15), row.reportedOn)
        assertEquals("홍삼농축액(고시형), 비타민C", row.functionalMaterials)
        assertEquals("면역력 증진", row.functionality)
        assertNull(row.precautions)
    }
}
