package com.example.iter.chat.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DetectorsTest {

    @Test
    fun `링크는 공백만 지운 텍스트에서 구조 문자가 살아 있어야 잡힌다`() {
        // digitsText로 매칭했다면 '.', '/'가 지워져서 링크를 못 알아본다 — 실제로 이
        // 버그를 겪고 detectTextBased를 별도로 분리했다.
        val stripped = Normalizer.stripWhitespace("자세한건 https://example.com/detail 참고").normalized

        val detections = Detectors.detectTextBased(stripped)

        assertThat(detections).anyMatch { it.category == ViolationCategory.EXTERNAL_LINK }
    }

    @Test
    fun `공백을 끼워 넣은 카톡도 잡는다`() {
        val stripped = Normalizer.stripWhitespace("카 톡 아이디 알려주세요").normalized

        val detections = Detectors.detectTextBased(stripped)

        assertThat(detections).anyMatch { it.category == ViolationCategory.MESSENGER_ID }
    }

    @Test
    fun `숫자 정규화 텍스트에서만 전화번호를 잡는다`() {
        val digits = Normalizer.normalizeDigits("010-1234-5678").normalized

        val detections = Detectors.detectDigitBased(digits)

        assertThat(detections).hasSize(1)
        assertThat(detections.first().category).isEqualTo(ViolationCategory.PHONE)
    }
}
