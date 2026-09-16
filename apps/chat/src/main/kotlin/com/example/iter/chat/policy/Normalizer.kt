package com.example.iter.chat.policy

// 마스킹은 원문 위치를 바꿔야 하므로, 정규화된 문자 하나가 원문의 몇 번째 문자에서
// 왔는지(originalIndex)를 같이 들고 있는다. 이 정규화들은 절대 "한 글자를 여러 글자로
// 늘리지" 않는다(늘리면 인덱스 매핑이 1:1로 안 돼서 깨진다) — 각 원문 문자는 그대로
// 두거나, 같은 자리에서 문자 하나로 바꾸거나, 통째로 버리는 것 셋 중 하나다.
data class NormalizedText(val normalized: String, val originalIndex: IntArray)

object Normalizer {

    private val fullWidthDigits = '０'..'９' // ０-９

    private val koreanDigitWords = mapOf(
        '공' to '0', '영' to '0',
        '일' to '1', '이' to '2', '삼' to '3', '사' to '4',
        '오' to '5', '육' to '6', '륙' to '6', '칠' to '7',
        '팔' to '8', '구' to '9',
    )

    // 숫자열 사이에 우회 목적으로 흔히 끼워 넣는 구분자.
    private val digitSeparators = setOf(' ', '\t', '\n', '-', '.', '_', '·', '/')

    private val whitespace = setOf(' ', '\t', '\n')

    // PHONE/ACCOUNT 탐지용. "010-1234-5678", "０１０..."(전각), "공일공 일이삼사..."(한글
    // 수사)가 전부 같은 "01012345678"로 보이게 만든다.
    fun normalizeDigits(text: String): NormalizedText = build(text) { ch ->
        when {
            ch in fullWidthDigits -> '0' + (ch - '０')
            koreanDigitWords.containsKey(ch) -> koreanDigitWords.getValue(ch)
            ch in digitSeparators -> null
            else -> ch
        }
    }

    // MESSENGER_ID/EXTERNAL_LINK/DIRECT_DEAL 탐지용. "카 톡"처럼 공백만 끼워 넣는
    // 우회는 막지만, URL의 구조 문자(. / :)는 그대로 둔다 — 지워버리면
    // "https://example.com"이 "httpsexamplecom"이 되어 링크 자체를 못 알아본다.
    fun stripWhitespace(text: String): NormalizedText = build(text) { ch ->
        if (ch in whitespace) null else ch
    }

    private inline fun build(text: String, map: (Char) -> Char?): NormalizedText {
        val normalized = StringBuilder(text.length)
        val originalIndex = ArrayList<Int>(text.length)
        for (i in text.indices) {
            val mapped = map(text[i])
            if (mapped != null) {
                normalized.append(mapped)
                originalIndex.add(i)
            }
        }
        return NormalizedText(normalized.toString(), originalIndex.toIntArray())
    }
}
