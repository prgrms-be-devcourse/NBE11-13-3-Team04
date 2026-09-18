package iter.chat.ws

import iter.chat.domain.ChatRoom
import iter.chat.redis.TicketPayload
import iter.chat.repository.ChatRoomRepository
import iter.chat.repository.MessageRepository
import iter.chat.repository.RoomParticipantRepository
import iter.chat.support.TestWebSocketClient
import com.redis.testcontainers.RedisContainer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

// IT-CHAT-011 (INTEGRATION_TEST_DESIGN.md) — 두 WebSocket 세션 간 메시지 송수신과 재접속.
//
// 방 자체는 IT-CHAT-010(grant/ticket 발급 흐름)에서 이미 검증했으므로, 여기서는
// 이미 존재하는 방을 리포지토리로 바로 준비하고(디자인 문서 8: "행위"가 검증 대상일
// 때만 HTTP를 쓴다 — 이 시나리오의 검증 대상은 WebSocket 송수신이다) 실제 서버에
// reactor-netty 클라이언트로 접속해 진짜 프레임을 주고받는다.
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class ChatWebSocketMessagingIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var reactiveRedisTemplate: ReactiveStringRedisTemplate

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var roomParticipantRepository: RoomParticipantRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val ownerId = 900L
    private val requesterId = 901L
    private val clients = mutableListOf<TestWebSocketClient>()

    @AfterEach
    fun closeClients() {
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    @Test
    fun `두 참여자가 서로 보낸 메시지를 순서대로 받고, 재접속해도 중복 없이 이력을 이어받는다`(): Unit = runBlocking {
        val room = chatRoomRepository.save(
            ChatRoom(equipmentId = 7001L, equipmentName = "카메라", ownerId = ownerId, requesterId = requesterId),
        )
        val roomId = room.id!!
        roomParticipantRepository.save(
            iter.chat.domain.RoomParticipant(roomId = roomId, userId = ownerId, nickname = "등록자"),
        )
        roomParticipantRepository.save(
            iter.chat.domain.RoomParticipant(roomId = roomId, userId = requesterId, nickname = "요청자"),
        )

        val ownerTicket = issueTicket(ownerId, "등록자")
        val requesterTicket = issueTicket(requesterId, "요청자")

        val ownerWs = connect(roomId, ownerTicket)
        val requesterWs = connect(roomId, requesterTicket)

        // 요청자가 먼저 한 마디 보낸다. 보낸 사람도 Pub/Sub 경로로 받으므로 둘 다 받아야 한다.
        requesterWs.send(sendFrame("안녕하세요, 대여 가능한가요?"))
        TestWebSocketClient.await { ownerWs.messageCount() == 1 && requesterWs.messageCount() == 1 }

        // 등록자가 답장한다 — 두 번째 메시지가 순서대로 뒤에 붙어야 한다.
        ownerWs.send(sendFrame("네 가능합니다!"))
        TestWebSocketClient.await { ownerWs.messageCount() == 2 && requesterWs.messageCount() == 2 }

        listOf(ownerWs, requesterWs).forEach { client ->
            val frames = client.messages().map { jsonMapper.readValue(it, OutgoingFrame::class.java) }
            assertThat(frames).extracting("type").containsExactly("MESSAGE", "MESSAGE")
            assertThat(frames[0].content).isEqualTo("안녕하세요, 대여 가능한가요?")
            assertThat(frames[1].content).isEqualTo("네 가능합니다!")
            assertThat(frames[0].id!!).isLessThan(frames[1].id!!)
        }

        val stored = messageRepository.findByRoomIdOrderByIdDesc(roomId, org.springframework.data.domain.PageRequest.of(0, 10))
            .toList()
        assertThat(stored).hasSize(2)
        assertThat(stored.map { it.content }).containsExactly("네 가능합니다!", "안녕하세요, 대여 가능한가요?")

        val lastMessageId = stored.first().id!!

        // 재접속: 기존 연결을 끊고 새 세션으로 다시 붙는다. REST 이력 조회는 중복 없이
        // 그대로 2건이어야 하고, 읽음 처리 후 참여자 레코드에 반영돼야 한다.
        requesterWs.close()
        val reconnected = connect(roomId, requesterTicket)
        reconnected.send(sendFrame("재접속 확인"))
        TestWebSocketClient.await { ownerWs.messageCount() == 3 }

        val history = webTestClient.get().uri("/api/v1/chat/rooms/$roomId/messages")
            .header("Authorization", "Bearer $ownerTicket")
            .exchange()
            .expectStatus().isOk
            .expectBody(MessagePageResponseTestDto::class.java)
            .returnResult().responseBody!!
        assertThat(history.messages).hasSize(3)
        assertThat(history.messages.map { it.id }.toSet()).hasSize(3)

        webTestClient.post().uri("/api/v1/chat/rooms/$roomId/read")
            .header("Authorization", "Bearer $ownerTicket")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("lastReadMessageId" to lastMessageId))
            .exchange()
            .expectStatus().isNoContent

        val ownerParticipant = roomParticipantRepository.findByRoomIdAndUserId(roomId, ownerId)
        assertThat(ownerParticipant?.lastReadMessageId).isEqualTo(lastMessageId)
    }

    // ------------------------------------------------------------------

    private data class MessageResponseTestDto(val id: Long, val content: String)
    private data class MessagePageResponseTestDto(val messages: List<MessageResponseTestDto>, val nextCursor: Long?)

    private fun TestWebSocketClient.messageCount() = messages().size

    private fun sendFrame(content: String) =
        jsonMapper.writeValueAsString(IncomingFrame(type = "SEND", content = content))

    private fun connect(roomId: Long, ticket: String): TestWebSocketClient {
        val client = TestWebSocketClient("ws://localhost:$port/ws/chat?roomId=$roomId&ticket=$ticket")
        clients += client
        client.awaitConnected()
        return client
    }

    private suspend fun issueTicket(userId: Long, nickname: String): String {
        val ticket = UUID.randomUUID().toString()
        val payload = jsonMapper.writeValueAsString(TicketPayload(userId, nickname))
        reactiveRedisTemplate.opsForValue()
            .set("chat:ticket:$ticket", payload, Duration.ofMinutes(10))
            .awaitSingleOrNull()
        return ticket
    }

    companion object {
        @Suppress("DEPRECATION")
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis: RedisContainer = RedisContainer(DockerImageName.parse("redis:7"))
    }
}
