package com.alkkuni.core.product

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// 표기 예는 로컬 실제 제품명(2026-11-13)에서 가져왔다.
class ExportNameTest {

    @Test
    fun `수출용 표기`() {
        val names = listOf(
            "애터미 석류미인(전량수출용)",
            "LactoDay ActivMe <전량수출용>",
            "홍삼정 (전량 수출용)",
            "비타민C (미국 수출용)",
            "뷰리클 이너뷰티 콜라겐(수출 전용)",
            "진쎈흑삼정프리미엄(수출)",
            "애터미 석류미인(태국)",
            "애터미 석류미인 (영국, 독일)",
            "애터미 석류미인(영국독일)",
            "애터미 헤모힘(말레이시아, 태국, 인도네시아, 콜롬비아, 인도)",
            "제이엠칼마본(태국용)",
            "애터미 퓨어 스피루리나 100% [싱가포르/캄보디아/몽골]",
        )
        // 실패한 이름이 모두 보이게 한 번에 비교한다.
        assertEquals(emptyList(), names.filterNot(ExportName::isExportOnly))
    }

    @Test
    fun `수출용이 아닌 표기`() {
        val names = listOf(
            "다이어트&유산균 (수출겸용)", // 국내에서도 판다
            "키드혼합유산균(수출명:GitPro)", // 국내 제품의 수출 이름 표기
            "황제정간보(수출명 : HwangJae JungGanBo)",
            "크릴오일(호주산)", // 원산지
            "비타민C 4000 영국산",
            "호주 프로폴리스", // 괄호 밖 국가명
            "멀티비타민(미국 FDA 등록 원료)", // 괄호 안에 국가명 말고 다른 말도 있다
            "애터미 헤모힘",
        )
        assertEquals(emptyList(), names.filter(ExportName::isExportOnly))
    }
}
