package iter.chat.ws

import iter.chat.domain.Message
import iter.chat.domain.MessageType
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FramesTest {

    @Test
    fun `OutgoingFrame_message는 Message 필드를 그대로 옮긴다`() {
        val sentAt = Instant.parse("2026-01-01T00:00:00Z")
        val message = Message(
            id = 1L,
            roomId = 2L,
            senderId = 3L,
            senderNickname = "손님",
            type = MessageType.USER,
            content = "안녕하세요",
            masked = false,
            sentAt = sentAt,
        )

        val frame = OutgoingFrame.message(message)

        assertThat(frame.type).isEqualTo("MESSAGE")
        assertThat(frame.id).isEqualTo(1L)
        assertThat(frame.roomId).isEqualTo(2L)
        assertThat(frame.senderId).isEqualTo(3L)
        assertThat(frame.senderNickname).isEqualTo("손님")
        assertThat(frame.content).isEqualTo("안녕하세요")
        assertThat(frame.masked).isFalse()
        assertThat(frame.sentAt).isEqualTo(sentAt)
        assertThat(frame.code).isNull()
    }

    @Test
    fun `OutgoingFrame_error는 code만 채우고 나머지는 비운다`() {
        val frame = OutgoingFrame.error("INVALID_FRAME")

        assertThat(frame.type).isEqualTo("ERROR")
        assertThat(frame.code).isEqualTo("INVALID_FRAME")
        assertThat(frame.id).isNull()
        assertThat(frame.content).isNull()
    }
}
