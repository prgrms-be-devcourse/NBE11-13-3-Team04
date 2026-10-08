package iter.common.pagination

import java.time.LocalDateTime

// createdAt 내림차순, id 내림차순 keyset 조회에 사용하는 내부 커서 값입니다.
@JvmRecord
data class CursorKey(
    val createdAt: LocalDateTime,
    val id: Long,
)
