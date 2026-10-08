package iter.chat.ws

import iter.chat.redis.RoomBroadcaster
import iter.chat.redis.TicketStore
import iter.chat.security.ChatPrincipal
import iter.chat.service.ChatMessageWriteService
import iter.chat.service.ChatRoomService
import iter.chat.service.ViolationService
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
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper

// 이 테스트는 "연결을 받아줄지 말지" 판단 분기만 본다 — 실제 메시지 송수신은
// session.send()/receive()가 걸려 있는 실시간 스트림이라 여기서는 단위 테스트로
// 검증하지 않는다(계획서의 "인스턴스 2개 띄워 소켓 간 도달 확인" 수동 E2E 대상).
//
// handshakeInfo.attributes(exchange attribute 전달)는 실제로는 항상 비어 있으므로
// (ChatWebSocketHandler 상단 주석 참고) 이 테스트는 핸드셰이크 URI의 ticket 쿼리
// 파라미터 + TicketStore 모킹으로 인증 분기를 재현한다.
@ExtendWith(MockitoExtension::class)
class ChatWebSocketHandlerTest {

    @Mock
    private lateinit var ticketStore: TicketStore

    @Mock
    private lateinit var chatRoomService: ChatRoomService

    @Mock
    private lateinit var chatMessageWriteService: ChatMessageWriteService

    @Mock
    private lateinit var violationService: ViolationService

    @Mock
    private lateinit var roomBroadcaster: RoomBroadcaster

    @Mock
    private lateinit var sessionRegistry: SessionRegistry

    @Spy
    private val jsonMapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    @InjectMocks
    private lateinit var handler: ChatWebSocketHandler

    @Test
    fun `roomId 쿼리파라미터가 없으면 1008로 닫고 참여자 조회를 하지 않는다`() = runTest {
        val session = fakeSession(uri = "/ws/chat?ticket=t")
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
        verify(chatRoomService, never()).findParticipant(any(), any())
    }

    @Test
    fun `ticket 쿼리파라미터가 없으면 1008로 닫는다`() = runTest {
        val session = fakeSession(uri = "/ws/chat?roomId=1")
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `티켓이 무효하면(티켓 스토어가 못 찾으면) 1008로 닫는다`() = runTest {
        val session = fakeSession(uri = "/ws/chat?roomId=1&ticket=expired")
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())
        whenever(ticketStore.resolve("expired")).thenReturn(null)

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `티켓은 유효하지만 이 방의 참여자가 아니면 1008로 닫는다`() = runTest {
        val principal = ChatPrincipal(99L, "낯선사람")
        val session = fakeSession(uri = "/ws/chat?roomId=1&ticket=valid")
        whenever(session.close(CloseStatus.POLICY_VIOLATION)).thenReturn(Mono.empty())
        whenever(ticketStore.resolve("valid")).thenReturn(principal)
        whenever(chatRoomService.findParticipant(1L, 99L)).thenReturn(null)

        StepVerifier.create(handler.handle(session)).verifyComplete()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    private fun fakeSession(uri: String): WebSocketSession {
        val session = mock<WebSocketSession>()
        val handshakeInfo = mock<HandshakeInfo>()
        whenever(handshakeInfo.uri).thenReturn(URI.create("http://localhost$uri"))
        whenever(handshakeInfo.headers).thenReturn(HttpHeaders())
        whenever(session.handshakeInfo).thenReturn(handshakeInfo)
        return session
    }
}
