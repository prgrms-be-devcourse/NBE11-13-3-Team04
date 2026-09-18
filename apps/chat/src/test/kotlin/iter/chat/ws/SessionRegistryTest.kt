package iter.chat.ws

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Sinks

private const val ROOM_A = 1L
private const val ROOM_B = 2L

class SessionRegistryTest {

    @Test
    fun `등록된 방으로 broadcastLocally하면 그 방 세션들의 sink에만 메시지가 들어간다`() {
        val registry = SessionRegistry()
        val (sessionA, sinkA) = fakeSession()
        val (sessionB, sinkB) = fakeSession()
        registry.register(ROOM_A, ChatSession(sessionA, sinkA))
        registry.register(ROOM_B, ChatSession(sessionB, sinkB))

        registry.broadcastLocally(ROOM_A, "hello")

        verify(sessionA).textMessage("hello")
        verify(sessionB, never()).textMessage(org.mockito.kotlin.any())
    }

    @Test
    fun `unregister한 세션은 이후 broadcastLocally를 받지 않는다`() {
        val registry = SessionRegistry()
        val (session, sink) = fakeSession()
        val chatSession = ChatSession(session, sink)
        registry.register(ROOM_A, chatSession)
        registry.unregister(ROOM_A, chatSession)

        registry.broadcastLocally(ROOM_A, "hello")

        verify(session, never()).textMessage(org.mockito.kotlin.any())
    }

    @Test
    fun `아무도 없는 방에 broadcastLocally해도 예외 없이 무시된다`() {
        val registry = SessionRegistry()

        assertThatCode { registry.broadcastLocally(999L, "hello") }.doesNotThrowAnyException()
    }

    private fun fakeSession(): Pair<WebSocketSession, Sinks.Many<WebSocketMessage>> {
        val session = mock<WebSocketSession>()
        val message = mock<WebSocketMessage>()
        whenever(session.textMessage(org.mockito.kotlin.any())).thenReturn(message)
        val sink = Sinks.many().unicast().onBackpressureBuffer<WebSocketMessage>()
        return session to sink
    }
}
