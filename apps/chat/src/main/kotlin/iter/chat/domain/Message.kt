package iter.chat.domain

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

enum class MessageType { USER, SYSTEM }

// content는 항상 마스킹이 끝난 뒤의 본문이다 — 원문은 어디에도 저장하지 않는다
// (ContentPolicy.apply()가 저장 전에 한 번 거친다).
@Table("messages")
data class Message(
    @Id
    val id: Long? = null,
    val roomId: Long,
    val senderId: Long? = null,
    val senderNickname: String? = null,
    val type: MessageType,
    val content: String,
    val masked: Boolean = false,
    val sentAt: Instant = Instant.now(),
)
