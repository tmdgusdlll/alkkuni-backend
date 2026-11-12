package com.alkkuni.core.brand

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrandMatcherTest {
    private val matcher = BrandMatcher(
        listOf(
            BrandMatcher.Alias("종근당", 1, false),
            BrandMatcher.Alias("종근당(鍾根堂)바이오", 2, false),
            BrandMatcher.Alias("종근당바이오", 2, false),
            BrandMatcher.Alias("애터미", 3, false),
            BrandMatcher.Alias("ATOMY", 3, false),
            BrandMatcher.Alias("필리", 4, true),
            BrandMatcher.Alias("유한", 5, false),
            BrandMatcher.Alias("유한M", null, false),
        ),
    )

    @Test
    fun `제품명이 표기로 시작하면 연결하고 띄어쓰기·대소문자·앞 괄호는 보지 않는다`() {
        assertEquals(3L, matcher.find("애터미 루테인"))
        assertEquals(3L, matcher.find("애터미파인자임"))
        assertEquals(3L, matcher.find("Atomy Probiotics"))
        assertEquals(3L, matcher.find("[애터미] 칼슘"))
    }

    @Test
    fun `여러 표기가 맞으면 가장 긴 표기를 쓴다`() {
        // 실제 제품명. "종근당"이 아니라 "종근당바이오"다
        assertEquals(2L, matcher.find("종근당(鍾根堂)바이오 발효홍삼유산균 골드"))
        assertEquals(1L, matcher.find("종근당 비타민C 1000mg"))
    }

    @Test
    fun `NBSP·전각 공백도 공백으로 본다`() {
        // 식약처 제품명 27개에 NBSP가 있다. 공백으로 안 보면 "종근당"으로 잘못 연결된다
        assertEquals(2L, matcher.find("종근당 바이오 발효홍삼"))
        assertEquals(2L, matcher.find("종근당　바이오 발효홍삼"))
        assertEquals(4L, matcher.find("필리 비타민C"))
    }

    @Test
    fun `첫 단어 전용 표기는 띄어 쓴 경우만 맞는다`() {
        assertEquals(4L, matcher.find("필리 비타민C"))
        assertNull(matcher.find("필리브 면역 비타민C"))
    }

    @Test
    fun `보류한 표기가 맞으면 더 짧은 표기로 내려가지 않고 브랜드 없음이다`() {
        assertNull(matcher.find("유한m비타민D2000IU"))
        assertEquals(5L, matcher.find("유한 프로바이오틱스"))
    }

    @Test
    fun `이름 중간에 나오는 표기는 연결하지 않는다`() {
        assertNull(matcher.find("홍삼정 애터미"))
        assertNull(matcher.find("고려홍삼농축액플러스"))
    }
}
