package iter.chat.ws

import iter.chat.domain.Message
import java.time.Instant

// 클라 → 서버. 텍스트 프레임 하나에 이 JSON 하나만 담긴다. content 없이 SEND가 오면
// 무효 프레임으로 처리한다(ChatWebSocketHandler).
data class IncomingFrame(val type: String, val content: String? = null)

// 서버 → 클라. type으로 종류를 구분한다 — MESSAGE/SYSTEM은 필드가 같고 ERROR만 code를 쓴다.
// 하나의 data class로 합쳐서 프레임 파싱 코드를 하나로 유지한다(sealed class로 나누면
// Jackson 쪽에 다형성 설정이 추가로 필요해진다).
data class OutgoingFrame(
    val type: String,
    val id: Long? = null,
    val roomId: Long? = null,
    val senderId: Long? = null,
    val senderNickname: String? = null,
    val content: String? = null,
    val masked: Boolean? = null,
    val sentAt: Instant? = null,
    val code: String? = null,
) {
    companion object {
        fun message(message: Message) = OutgoingFrame(
            type = "MESSAGE",
            id = message.id,
            roomId = message.roomId,
            senderId = message.senderId,
            senderNickname = message.senderNickname,
            content = message.content,
            masked = message.masked,
            sentAt = message.sentAt,
        )

        fun error(code: String) = OutgoingFrame(type = "ERROR", code = code)
    }
}
