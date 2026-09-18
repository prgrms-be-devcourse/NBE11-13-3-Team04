package iter.composition.ai

import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class ReportTextSanitizer {
    // 외부 AI에 전달하기 전에 이메일·휴대전화 번호를 마스킹하고 입력 길이를 제한합니다.
    fun addPublicContent(content: MutableMap<String, String>, key: String, value: String?) {
        if (value.isNullOrBlank()) {
            return
        }

        var sanitized = value.trim { character -> character.code <= 0x20 }

        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[이메일 제거]")
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[전화번호 제거]")

        content[key] = sanitized.take(MAX_PUBLIC_TEXT_LENGTH)
    }

    // 회원 신고에서도 거래 관련 표현이 있을 때만 두 회원의 최근 거래 정보를 추가합니다.
    fun mentionsTransaction(description: String?): Boolean =
        !description.isNullOrBlank() && TRANSACTION_KEYWORDS.any(description::contains)

    private companion object {
        private const val MAX_PUBLIC_TEXT_LENGTH = 1_200
        private val EMAIL_PATTERN: Pattern = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        )
        private val PHONE_PATTERN: Pattern = Pattern.compile(
            "(?<!\\d)(?:01[016789])[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"
        )
        private val TRANSACTION_KEYWORDS = listOf(
            "대여",
            "렌탈",
            "예약",
            "배송",
            "수령",
            "반납",
            "미반납",
            "연체",
            "결제",
            "환불",
            "파손",
            "고장",
            "흠집",
            "스크래치",
            "분실",
            "구성품"
        )
    }
}
