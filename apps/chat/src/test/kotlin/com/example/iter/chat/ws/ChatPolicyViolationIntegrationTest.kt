package com.example.iter.chat.ws

import com.example.iter.chat.domain.ChatRoom
import com.example.iter.chat.domain.MessageType
import com.example.iter.chat.domain.RoomParticipant
import com.example.iter.chat.redis.TicketPayload
import com.example.iter.chat.repository.ChatRoomRepository
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.repository.RoomParticipantRepository
import com.example.iter.chat.repository.ViolationRepository
import com.example.iter.chat.support.TestWebSocketClient
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
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

// IT-CHAT-012 (INTEGRATION_TEST_DESIGN.md) — 정책 위반 메시지 처리.
//
// ContentPolicy/ViolationService의 실제 규칙(ViolationServiceTest 등 단위 테스트가 이미
// 커버)을 다시 증명하지 않는다 — 여기서는 실제 WebSocket 연결을 통해 "위반 → 마스킹 →
// 시스템 메시지 브로드캐스트 → 3회째 방 뮤트 → 뮤트 중 전송 거부"가 실제 서버 경로 전체를
// 거쳐 끝까지 이어지는지만 확인한다.
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class ChatPolicyViolationIntegrationTest {

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

    @Autowired
    private lateinit var violationRepository: ViolationRepository

    private val ownerId = 910L
    private val requesterId = 911L
    private val clients = mutableListOf<TestWebSocketClient>()

    @AfterEach
    fun closeClients() {
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    @Test
    fun `연락처가 담긴 메시지는 저장 전에 마스킹되고, 세 번째 위반부터 방이 뮤트된다`(): Unit = runBlocking {
        val room = chatRoomRepository.save(
            ChatRoom(equipmentId = 7002L, equipmentName = "드론", ownerId = ownerId, requesterId = requesterId),
        )
        val roomId = room.id!!
        roomParticipantRepository.save(RoomParticipant(roomId = roomId, userId = ownerId, nickname = "등록자"))
        roomParticipantRepository.save(RoomParticipant(roomId = roomId, userId = requesterId, nickname = "요청자"))

        val ticket = issueTicket(requesterId, "요청자")
        val ownerTicket = issueTicket(ownerId, "등록자")
        val ws = connect(roomId, ticket)
        val ownerWs = connect(roomId, ownerTicket)

        // 1~2번째 위반: 마스킹되어 저장되고, 상대방에게도 경고 시스템 메시지가 전달된다.
        // 아직 뮤트되지는 않는다(정확히 3회째부터).
        repeat(2) { i ->
            ws.send(sendFrame("연락처는 010-1234-567$i 입니다"))
            TestWebSocketClient.await {
                ws.received().count { frame ->
                    val message = jsonMapper.readValue(frame, OutgoingFrame::class.java)
                    message.senderId == requesterId
                } == i + 1
            }
        }
        TestWebSocketClient.await { ownerWs.received().count { it.contains("개인정보 공유") } == 2 }

        val afterTwo = messageRepository.findByRoomIdOrderByIdDesc(
            roomId,
            org.springframework.data.domain.PageRequest.of(0, 10),
        )
        val storedContents = afterTwo.toList()
            .filter { it.type == MessageType.USER }
            .map { it.content }
        assertThat(storedContents).hasSize(2)
        assertThat(storedContents).allMatch { it.contains("[전화번호 차단]") }
        assertThat(storedContents).noneMatch { it.contains("010-") }

        assertThat(violationRepository.countByUserIdAndOccurredAtAfter(requesterId, Instant.now().minusSeconds(60)))
            .isEqualTo(2L)
        assertThat(roomParticipantRepository.findByRoomIdAndUserId(roomId, requesterId)?.mutedUntil).isNull()

        // 3번째 위반: 이 시점에 방이 뮤트되고, 뮤트 시스템 메시지가 추가로 방송된다.
        ws.send(sendFrame("연락처는 010-1234-5679 입니다"))
        TestWebSocketClient.await { ownerWs.received().count { it.contains("24시간") } == 1 }

        val muted = roomParticipantRepository.findByRoomIdAndUserId(roomId, requesterId)
        assertThat(muted?.mutedUntil).isNotNull
        assertThat(muted!!.mutedUntil!!).isAfter(Instant.now())

        val messageCountBeforeMutedSend = messageRepository.findByRoomIdOrderByIdDesc(
            roomId,
            org.springframework.data.domain.PageRequest.of(0, 50),
        ).toList().size

        // 뮤트된 상태에서 보낸 메시지는 저장되지 않고 MUTED 에러 프레임만 돌아온다.
        ws.send(sendFrame("그래도 보낼게요"))
        TestWebSocketClient.await { ws.received().any { it.contains("\"code\":\"MUTED\"") } }

        val messageCountAfterMutedSend = messageRepository.findByRoomIdOrderByIdDesc(
            roomId,
            org.springframework.data.domain.PageRequest.of(0, 50),
        ).toList().size
        assertThat(messageCountAfterMutedSend).isEqualTo(messageCountBeforeMutedSend)
    }

    @Test
    fun `결제 완료(TRADE) 단계에서는 연락처를 그대로 보낼 수 있다`(): Unit = runBlocking {
        val room = chatRoomRepository.save(
            ChatRoom(
                equipmentId = 7003L,
                equipmentName = "노트북",
                ownerId = ownerId,
                requesterId = requesterId,
                stage = com.example.iter.chat.domain.RoomStage.TRADE,
                rentalId = 12345L,
            ),
        )
        val roomId = room.id!!
        roomParticipantRepository.save(RoomParticipant(roomId = roomId, userId = ownerId, nickname = "등록자"))
        roomParticipantRepository.save(RoomParticipant(roomId = roomId, userId = requesterId, nickname = "요청자"))

        val ticket = issueTicket(requesterId, "요청자")
        val ws = connect(roomId, ticket)

        ws.send(sendFrame("배송 연락처는 010-1234-5678 입니다"))
        TestWebSocketClient.await { ws.received().isNotEmpty() }

        val stored = messageRepository.findByRoomIdOrderByIdDesc(
            roomId,
            org.springframework.data.domain.PageRequest.of(0, 10),
        ).toList()
        assertThat(stored)
            .withFailMessage("수신 프레임: %s", ws.received())
            .hasSize(1)
        assertThat(stored.first().content).isEqualTo("배송 연락처는 010-1234-5678 입니다")
        assertThat(stored.first().masked).isFalse()
        assertThat(violationRepository.countByUserIdAndOccurredAtAfter(requesterId, Instant.now().minusSeconds(60)))
            .isZero()
    }

    // ------------------------------------------------------------------

    private fun TestWebSocketClient.received(): List<String> = messages()

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
