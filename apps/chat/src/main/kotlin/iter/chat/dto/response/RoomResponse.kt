package iter.chat.dto.response

import iter.chat.domain.RoomStage
import java.time.Instant

data class RoomResponse(
    val roomId: Long,
    val equipmentId: Long,
    val equipmentName: String,
    val stage: RoomStage,
    val createdAt: Instant,
)

// 목록 화면에서 상대방 정보 + 마지막 메시지 + 미읽음 수까지 한 번에 필요해서 별도 응답으로 둔다.
// counterpart는 "나 말고 이 방의 상대방" — owner/requester 중 내가 아닌 쪽이다.
data class RoomSummaryResponse(
    val roomId: Long,
    val equipmentId: Long,
    val equipmentName: String,
    val stage: RoomStage,
    val counterpartId: Long,
    val counterpartNickname: String,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
    val unreadCount: Long,
)
