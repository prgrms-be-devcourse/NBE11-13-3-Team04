package iter.chat.policy

data class Detection(val category: ViolationCategory, val range: IntRange)

// 정직한 한계: 완벽 차단은 불가능하다(오탐·미탐 존재). 사전은 여기서 고정값으로 두되,
// 운영하며 튜닝이 필요해지면 application.yaml로 옮긴다(.docs/05-chat-and-realtime.md).
object Detectors {

    private val phoneRegex = Regex("01[016789][0-9]{7,8}")

    // 계좌번호는 은행 고유 포맷이 제각각이라 규칙화가 어렵다 — 연속 10~14자리 숫자를
    // 전부 의심 대상으로 본다. 전화번호로 이미 잡힌 구간과 겹치면 계좌로는 중복 표시하지
    // 않는다(detectDigitBased에서 처리).
    private val accountRegex = Regex("[0-9]{10,14}")

    private val messengerRegex = Regex(
        "카카오톡|카톡|kakao|톡아이디|오픈채팅|오픈챗|open\\.kakao\\.com",
        RegexOption.IGNORE_CASE,
    )

    private val linkRegex = Regex(
        "https?://\\S+|www\\.[a-z0-9.-]+\\.[a-z]{2,}|[a-z0-9-]+\\.(com|net|kr|io|me|link)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val directDealWords = listOf(
        "현금", "직거래", "만나서", "계좌이체", "송금", "깎아", "네고", "수수료빼고",
    )

    // Normalizer.normalizeDigits() 결과를 상대로 매칭한다 — 구분자가 지워지고 숫자가
    // 통일된 뒤라 정규식이 단순해진다. PHONE/ACCOUNT만 여기서 다룬다.
    fun detectDigitBased(normalizedDigits: String): List<Detection> {
        val detections = mutableListOf<Detection>()

        phoneRegex.findAll(normalizedDigits).forEach { detections += Detection(ViolationCategory.PHONE, it.range) }

        accountRegex.findAll(normalizedDigits).forEach { match ->
            val overlapsPhone = detections.any {
                it.category == ViolationCategory.PHONE && it.range.overlapsWith(match.range)
            }
            if (!overlapsPhone) {
                detections += Detection(ViolationCategory.ACCOUNT, match.range)
            }
        }

        return detections
    }

    // Normalizer.stripWhitespace() 결과를 상대로 매칭한다 — URL의 구조 문자(. / :)는
    // 살아 있어야 링크를 알아볼 수 있어서, 숫자 정규화(digitSeparators 제거)는 쓰지 않는다.
    fun detectTextBased(strippedWhitespace: String): List<Detection> {
        val detections = mutableListOf<Detection>()

        messengerRegex.findAll(strippedWhitespace).forEach { detections += Detection(ViolationCategory.MESSENGER_ID, it.range) }
        linkRegex.findAll(strippedWhitespace).forEach { detections += Detection(ViolationCategory.EXTERNAL_LINK, it.range) }

        directDealWords.forEach { word ->
            var fromIndex = strippedWhitespace.indexOf(word)
            while (fromIndex >= 0) {
                detections += Detection(ViolationCategory.DIRECT_DEAL, fromIndex until fromIndex + word.length)
                fromIndex = strippedWhitespace.indexOf(word, fromIndex + word.length)
            }
        }

        return detections
    }

    private fun IntRange.overlapsWith(other: IntRange) = first <= other.last && other.first <= last
}
