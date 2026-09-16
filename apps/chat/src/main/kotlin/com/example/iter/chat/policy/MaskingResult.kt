package com.example.iter.chat.policy

// masked가 false면 violations는 항상 비어 있고 maskedContent == 원문이다.
// 원문은 이 결과가 만들어진 뒤로는 어디에도 남지 않는다 — 호출부(ChatMessageWriteService)가
// maskedContent만 저장한다.
data class MaskingResult(
    val maskedContent: String,
    val violations: List<ViolationCategory>,
    val masked: Boolean,
) {
    companion object {
        fun clean(content: String) = MaskingResult(content, emptyList(), false)
    }
}
