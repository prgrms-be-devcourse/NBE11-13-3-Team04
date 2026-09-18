package iter.chat.domain

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

enum class RoomStage {
    // 결제 전 문의 — ContentPolicy가 적용된다.
    INQUIRY,
    // 결제 확정 후 — 배송 관련 대화가 필요해 정책을 끈다.
    TRADE,
    CLOSED,
}

@Table("chat_rooms")
data class ChatRoom(
    @Id
    val id: Long? = null,
    val equipmentId: Long,
    val equipmentName: String,
    val ownerId: Long,
    val requesterId: Long,
    val rentalId: Long? = null,
    val stage: RoomStage = RoomStage.INQUIRY,
    val createdAt: Instant = Instant.now(),
)
