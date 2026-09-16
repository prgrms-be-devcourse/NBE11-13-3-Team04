package com.example.iter.chat.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NormalizerTest {

    @Test
    fun `구분자(공백, 하이픈, 점)는 지운다`() {
        val result = Normalizer.normalizeDigits("010-1234 5678.")

        assertThat(result.normalized).isEqualTo("01012345678")
    }

    @Test
    fun `전각 숫자는 반각으로 바꾼다`() {
        val result = Normalizer.normalizeDigits("０１０１２３４５６７８")

        assertThat(result.normalized).isEqualTo("01012345678")
    }

    @Test
    fun `한글 수사는 아라비아 숫자로 바꾼다`() {
        val result = Normalizer.normalizeDigits("공일공 일이삼사 오육칠팔")

        assertThat(result.normalized).isEqualTo("01012345678")
    }

    @Test
    fun `한글 문장은 구분자 없이 그대로 남는다`() {
        val result = Normalizer.normalizeDigits("안녕하세요")

        assertThat(result.normalized).isEqualTo("안녕하세요")
    }

    @Test
    fun `originalIndex는 정규화된 각 글자가 원문의 몇 번째 글자였는지를 그대로 가리킨다`() {
        // "a-b" → 'a'(0), '-'(지워짐), 'b'(2) → 정규화 결과 "ab", 인덱스는 [0, 2]
        val result = Normalizer.normalizeDigits("a-b")

        assertThat(result.normalized).isEqualTo("ab")
        assertThat(result.originalIndex).containsExactly(0, 2)
    }

    @Test
    fun `stripWhitespace는 공백만 지우고 URL 구조 문자는 그대로 둔다`() {
        val result = Normalizer.stripWhitespace("https://example.com/detail")

        assertThat(result.normalized).isEqualTo("https://example.com/detail")
    }

    @Test
    fun `stripWhitespace는 띄어 쓴 카톡도 붙여 준다`() {
        val result = Normalizer.stripWhitespace("카 톡")

        assertThat(result.normalized).isEqualTo("카톡")
    }
}
