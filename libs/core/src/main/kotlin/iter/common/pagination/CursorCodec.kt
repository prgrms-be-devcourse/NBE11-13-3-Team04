package iter.common.pagination

import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * 커서 내부 값을 URL-safe Base64 문자열로 변환합니다.
 * 커서는 페이지 이동 위치일 뿐 권한 검증이나 보안 토큰으로 사용하지 않습니다.
 */
object CursorCodec {

    private const val DELIMITER = "|"

    @JvmStatic
    fun encode(cursorKey: CursorKey): String {
        val value = "${cursorKey.createdAt}$DELIMITER${cursorKey.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    @JvmStatic
    fun decode(cursor: String?): CursorKey? {
        if (cursor.isNullOrBlank()) {
            return null
        }

        try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8)
            val values = decoded.split(DELIMITER)

            if (values.size != 2) {
                throw invalidCursor()
            }

            val createdAt = LocalDateTime.parse(values[0])
            val id = values[1].toLong()

            if (id <= 0) {
                throw invalidCursor()
            }

            return CursorKey(createdAt, id)
        } catch (e: IllegalArgumentException) {
            throw invalidCursor()
        } catch (e: DateTimeParseException) {
            throw invalidCursor()
        }
    }

    private fun invalidCursor(): CustomException =
        CustomException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 커서입니다.")
}
