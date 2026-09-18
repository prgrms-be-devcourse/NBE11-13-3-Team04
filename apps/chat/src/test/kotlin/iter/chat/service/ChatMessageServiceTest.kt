package iter.chat.service

import iter.chat.domain.Message
import iter.chat.domain.MessageType
import iter.chat.repository.MessageRepository
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

private const val ROOM_ID = 1L

@ExtendWith(MockitoExtension::class)
class ChatMessageServiceTest {

    @Mock
    private lateinit var messageRepository: MessageRepository

    @InjectMocks
    private lateinit var chatMessageService: ChatMessageService

    @Test
    fun `커서가 없으면 최신 메시지부터 조회한다`() = runTest {
        whenever(messageRepository.findByRoomIdOrderByIdDesc(eq(ROOM_ID), any()))
            .thenReturn(flowOf(message(id = 30L), message(id = 29L)))

        val page = chatMessageService.getMessages(ROOM_ID, cursor = null, size = 2)

        assertThat(page.messages).extracting<Long> { it.id }.containsExactly(30L, 29L)
    }

    @Test
    fun `요청한 size만큼 꽉 차면 마지막 메시지 id를 nextCursor로 준다`() = runTest {
        whenever(messageRepository.findByRoomIdOrderByIdDesc(eq(ROOM_ID), any()))
            .thenReturn(flowOf(message(id = 30L), message(id = 29L)))

        val page = chatMessageService.getMessages(ROOM_ID, cursor = null, size = 2)

        assertThat(page.nextCursor).isEqualTo(29L)
    }

    @Test
    fun `size보다 적게 오면 더 이상 페이지가 없다는 뜻이라 nextCursor가 null이다`() = runTest {
        whenever(messageRepository.findByRoomIdOrderByIdDesc(eq(ROOM_ID), any()))
            .thenReturn(flowOf(message(id = 30L)))

        val page = chatMessageService.getMessages(ROOM_ID, cursor = null, size = 2)

        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `커서가 있으면 그보다 오래된 메시지를 조회한다`() = runTest {
        whenever(messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(eq(ROOM_ID), eq(29L), any()))
            .thenReturn(flowOf(message(id = 28L)))

        val page = chatMessageService.getMessages(ROOM_ID, cursor = 29L, size = 20)

        assertThat(page.messages).extracting<Long> { it.id }.containsExactly(28L)
    }

    private fun message(id: Long) = Message(
        id = id,
        roomId = ROOM_ID,
        senderId = 1L,
        senderNickname = "손님",
        type = MessageType.USER,
        content = "안녕하세요",
        masked = false,
        sentAt = Instant.now(),
    )
}
