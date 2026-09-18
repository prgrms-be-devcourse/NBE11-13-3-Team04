package iter.chat.api

import iter.chat.domain.RoomStage
import iter.chat.redis.GrantPayload
import iter.chat.redis.TicketPayload
import iter.chat.repository.ChatRoomRepository
import iter.chat.repository.RoomParticipantRepository
import com.redis.testcontainers.RedisContainer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
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

// IT-CHAT-010 (INTEGRATION_TEST_DESIGN.md) — 문의 grant 발급 -> ticket 교환 -> 방 생성.
//
// monolith(POST /api/v1/chat/inquiry-grants, POST /api/v1/chat/tickets)가 실제로 Redis에
// 써 두는 값(chat:grant:*, chat:ticket:*, GrantPayload/TicketPayload와 같은 필드 이름)을
// 이 테스트가 직접 흉내 낸다 — monolith를 띄우지 않고도 두 서비스가 공유하는 Redis 계약과
// chat 쪽 소비 로직(GrantStore.consume의 GETDEL, TicketAuthWebFilter)을 함께 검증한다.
// 두 서비스를 실제로 함께 띄우는 검증은 integration-tests/system/test_system_smoke.py가 담당한다.
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class ChatRoomCreationFlowIntegrationTest {

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

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val equipmentId = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    private val ownerId = 100L
    private val requesterId = 200L
    private val outsiderId = 300L

    @Test
    fun `grant와 ticket으로 방을 생성하면 참여자 두 명이 저장되고 grant는 한 번만 쓸 수 있다`(): Unit = runBlocking {
        val ticket = issueTicket(requesterId, "요청자")
        val grant = issueGrant(equipmentId, ownerId, requesterId)

        // 발급 직후 TTL이 monolith의 발급 정책(60초)대로 걸려 있는지 확인한다.
        assertThat(grantTtlSeconds(grant)).isGreaterThan(0).isLessThanOrEqualTo(60)

        val roomId = createRoom(ticket, grant)
            .expectStatus().isCreated
            .expectBody(RoomIdResponse::class.java)
            .returnResult().responseBody!!.roomId

        val room = chatRoomRepository.findById(roomId)
        assertThat(room).isNotNull
        assertThat(room!!.equipmentId).isEqualTo(equipmentId)
        assertThat(room.ownerId).isEqualTo(ownerId)
        assertThat(room.requesterId).isEqualTo(requesterId)
        assertThat(room.stage).isEqualTo(RoomStage.INQUIRY)

        val participants = roomParticipantRepository.findByRoomId(roomId).toList()
        assertThat(participants).hasSize(2)
        assertThat(participants.map { it.userId }).containsExactlyInAnyOrder(ownerId, requesterId)

        // GrantStore.consume()이 GETDEL이므로 사용한 grant는 Redis에서 사라져야 한다.
        assertThat(reactiveRedisTemplate.opsForValue().get("chat:grant:$grant").awaitSingleOrNull()).isNull()

        // 같은 grant로 다시 요청하면 이미 소비되어 실패한다(일회성 검증).
        createRoom(ticket, grant)
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("GRANT_INVALID_OR_EXPIRED")
    }

    @Test
    fun `발급받은 본인이 아니면 grant를 쓸 수 없고, 실패한 시도라도 grant는 이미 소비된다`(): Unit = runBlocking {
        val grant = issueGrant(equipmentId, ownerId, requesterId)
        val outsiderTicket = issueTicket(outsiderId, "제3자")

        createRoom(outsiderTicket, grant)
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("GRANT_USER_MISMATCH")

        // GrantStore.consume()은 검증에 앞서 GETDEL부터 하므로, 본인 확인에 실패한
        // 시도라도 grant는 이미 지워진다 — 원래 요청자가 다시 시도해도 실패한다.
        val ticket = issueTicket(requesterId, "요청자")
        createRoom(ticket, grant)
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("GRANT_INVALID_OR_EXPIRED")
    }

    @Test
    fun `티켓 없이 요청하면 401이다`() {
        val grant = runBlocking { issueGrant(equipmentId, ownerId, requesterId) }

        webTestClient.post().uri("/api/v1/chat/rooms")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("grantToken" to grant))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("$.code").isEqualTo("TICKET_INVALID")
    }

    @Test
    fun `방 참여자만 읽음 처리를 할 수 있다`(): Unit = runBlocking {
        val ticket = issueTicket(requesterId, "요청자")
        val grant = issueGrant(equipmentId, ownerId, requesterId)
        val roomId = createRoom(ticket, grant)
            .expectStatus().isCreated
            .expectBody(RoomIdResponse::class.java)
            .returnResult().responseBody!!.roomId

        val outsiderTicket = issueTicket(outsiderId, "제3자")
        webTestClient.post().uri("/api/v1/chat/rooms/$roomId/read")
            .header("Authorization", "Bearer $outsiderTicket")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("lastReadMessageId" to 0))
            .exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("ROOM_ACCESS_DENIED")

        webTestClient.post().uri("/api/v1/chat/rooms/$roomId/read")
            .header("Authorization", "Bearer $ticket")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("lastReadMessageId" to 0))
            .exchange()
            .expectStatus().isNoContent

        val participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, requesterId)
        assertThat(participant).isNotNull
        assertThat(participant!!.lastReadMessageId).isEqualTo(0L)
    }

    // ------------------------------------------------------------------

    private data class RoomIdResponse(val roomId: Long)

    private fun createRoom(ticket: String, grantToken: String) =
        webTestClient.post().uri("/api/v1/chat/rooms")
            .header("Authorization", "Bearer $ticket")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("grantToken" to grantToken))
            .exchange()

    // monolith(ChatTicketService)가 chat:ticket:{ticket}에 쓰는 값과 같은 형태를 그대로 재현한다.
    private suspend fun issueTicket(userId: Long, nickname: String): String {
        val ticket = UUID.randomUUID().toString()
        val payload = jsonMapper.writeValueAsString(TicketPayload(userId, nickname))
        reactiveRedisTemplate.opsForValue()
            .set("chat:ticket:$ticket", payload, Duration.ofMinutes(10))
            .awaitSingleOrNull()
        return ticket
    }

    // monolith(ChatInquiryGrantService)가 chat:grant:{token}에 쓰는 값과 같은 형태를 그대로 재현한다.
    private suspend fun issueGrant(equipmentId: Long, ownerId: Long, requesterId: Long): String {
        val grant = UUID.randomUUID().toString()
        val payload = jsonMapper.writeValueAsString(
            GrantPayload(equipmentId, "테스트 드릴", ownerId, "등록자", requesterId, "요청자"),
        )
        reactiveRedisTemplate.opsForValue()
            .set("chat:grant:$grant", payload, Duration.ofSeconds(60))
            .awaitSingleOrNull()
        return grant
    }

    private suspend fun grantTtlSeconds(grant: String): Long =
        reactiveRedisTemplate.getExpire("chat:grant:$grant").awaitSingleOrNull()?.seconds ?: -1

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
