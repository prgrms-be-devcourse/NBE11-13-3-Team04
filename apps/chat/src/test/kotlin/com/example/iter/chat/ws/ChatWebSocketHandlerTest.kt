package com.example.iter.chat.ws

import com.example.iter.chat.domain.RoomParticipant
import com.example.iter.chat.redis.RoomBroadcaster
import com.example.iter.chat.security.CHAT_PRINCIPAL_ATTRIBUTE
import com.example.iter.chat.security.ChatPrincipal
import com.example.iter.chat.service.ChatMessageWriteService
import com.example.iter.chat.service.ChatRoomService
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper

// 이 테스트는 "연결을 받아줄지 말지" 판단 분기만 본다 — 실제 메시지 송수신은
// session.send()/receive()가 걸려 있는 실시간 스트림이라 여기서는 단위 테스트로
// 검증하지 않는다(계획서의 "인스턴스 2개 띄워 소켓 간 도달 확인" 수동 E2E 대상).
@ExtendWith(MockitoExtension::class)
class ChatWebSocketHandlerTest {

    @Mock
    private lateinit var chatRoomService: ChatRoomService

    @Mock
    private lateinit var chatMessageWriteService: ChatMessageWriteService

    @Mock
    private lateinit var roomBroadcaster: RoomBroadcaster

    @Mock
    private lateinit var sessionRegistry: SessionRegistry

    @Spy
    private val jsonMapper: JsonMapper = JsonMapper.builder().build()

    @InjectMocks
    private lateinit var handler: ChatWebSocketHandler

    @Test
    fun `roomId 쿼리파라미터가 없으면 1008로 닫고 참여자 조회를 하지 않는다`() = runTest {
        val session = fakeSession(uri = "/ws/chat", attributes = emptyMap())
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
        verify(chatRoomService, never()).findParticipant(any(), any())
    }

    @Test
    fun `principal이 없으면(티켓 무효) 1008로 닫는다`() = runTest {
        val session = fakeSession(uri = "/ws/chat?roomId=1", attributes = emptyMap())
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `principal은 있지만 이 방의 참여자가 아니면 1008로 닫는다`() = runTest {
        val principal = ChatPrincipal(99L, "낯선사람")
        val session = fakeSession(uri = "/ws/chat?roomId=1", attributes = mapOf(CHAT_PRINCIPAL_ATTRIBUTE to principal))
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())
        whenever(chatRoomService.findParticipant(1L, 99L)).thenReturn(null)

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    private fun fakeSession(uri: String, attributes: Map<String, Any>): WebSocketSession {
        val session = mock<WebSocketSession>()
        val handshakeInfo = mock<HandshakeInfo>()
        whenever(handshakeInfo.uri).thenReturn(URI.create("http://localhost$uri"))
        whenever(handshakeInfo.attributes).thenReturn(attributes)
        whenever(session.handshakeInfo).thenReturn(handshakeInfo)
        return session
    }
}
