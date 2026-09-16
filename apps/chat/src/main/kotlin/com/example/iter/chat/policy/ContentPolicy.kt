package com.example.iter.chat.policy

// 결제 전 문의 단계(stage=INQUIRY)에서만 호출된다(ChatMessageWriteService가 stage를
// 보고 결정) — 결제 후에는 배송 관련 대화가 필요해서 정책을 끈다.
object ContentPolicy {

    fun apply(rawContent: String): MaskingResult {
        val digitsText = Normalizer.normalizeDigits(rawContent)
        val whitespaceText = Normalizer.stripWhitespace(rawContent)

        // 두 정규화가 원문 좌표계로 서로 다르게 지워서(digits는 . / - 도 지우고,
        // whitespace는 공백만 지운다) 각자 원문 인덱스로 되돌린 뒤에 합쳐야 한다 —
        // 정규화된 좌표를 그대로 합치면 서로 다른 걸 가리키게 된다.
        val digitDetections = Detectors.detectDigitBased(digitsText.normalized)
            .map { it.category to it.toOriginalRange(digitsText) }
        val textDetections = Detectors.detectTextBased(whitespaceText.normalized)
            .map { it.category to it.toOriginalRange(whitespaceText) }

        val originalSpans = (digitDetections + textDetections).sortedBy { it.second.first }
        if (originalSpans.isEmpty()) {
            return MaskingResult.clean(rawContent)
        }

        // 원문 좌표로 옮기면 서로 다른 카테고리의 구간이 겹치거나 맞붙을 수 있다(예:
        // 전화번호 바로 뒤에 "톡ID" 같은 단어). 겹치면 하나의 마스킹 블록으로 합쳐서
        // "[전화번호 차단][메신저 ID 차단]"처럼 라벨이 이어 붙는 것을 막는다.
        val merged = mutableListOf<MergedSpan>()
        for ((category, range) in originalSpans) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.end + 1) {
                last.end = maxOf(last.end, range.last)
                last.categories += category
            } else {
                merged += MergedSpan(range.first, range.last, mutableSetOf(category))
            }
        }

        val builder = StringBuilder()
        var cursor = 0
        for (span in merged) {
            builder.append(rawContent, cursor, span.start)
            val label = span.categories.joinToString("/") { it.label }
            builder.append('[').append(label).append(" 차단]")
            cursor = span.end + 1
        }
        builder.append(rawContent, cursor, rawContent.length)

        return MaskingResult(
            maskedContent = builder.toString(),
            violations = merged.flatMap { it.categories }.distinct(),
            masked = true,
        )
    }

    private fun Detection.toOriginalRange(normalizedText: NormalizedText): IntRange {
        val start = normalizedText.originalIndex[range.first]
        val end = normalizedText.originalIndex[range.last]
        return start..end
    }

    private class MergedSpan(var start: Int, var end: Int, val categories: MutableSet<ViolationCategory>)
}
