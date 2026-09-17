package com.example.iter.chat.event

import com.example.iter.chat.domain.ChatRoom
import com.example.iter.chat.domain.RoomStage
import com.example.iter.chat.repository.ChatRoomRepository
import com.redis.testcontainers.RedisContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.annotation.DirtiesContext
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

// Docker가 있어야 돈다 — integrationTest 태스크 전용(apps/chat/build.gradle 참고).
// monolith가 실제로 하는 XADD를 이 테스트에서 StringRedisTemplate으로 그대로 재현해서,
// ChatIntegrationEventConsumer가 진짜 Redis Stream 소비자 그룹으로 그 이벤트를 받아
// 방 stage를 전환하는지까지 끝에서 끝으로 확인한다(단위 테스트로는 못 보는 부분).
//
// 소비는 ApplicationReadyEvent 시점에 시작한 백그라운드 구독(별도 스레드)이라, 반영될
// 때까지 폴링한다 — Awaitility 등 새 테스트 의존성을 추가하지 않고 간단히 재시도한다.
@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class ChatIntegrationEventConsumerIntegrationTest {

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var reactiveRedisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `Stream에 결제확정 이벤트를 XADD하면 문의방이 TRADE로 전환된다`(): Unit = runBlocking {
        val room = chatRoomRepository.save(
            ChatRoom(equipmentId = 111L, equipmentName = "드릴", ownerId = 10L, requesterId = 20L),
        )
        assertThat(room.stage).isEqualTo(RoomStage.INQUIRY)

        val payload = """{"eventId":"evt-1","rentalId":999,"equipmentId":111,"renterId":20,"ownerId":10,"occurredAt":"2026-01-01T00:00:00Z"}"""
        reactiveRedisTemplate.opsForStream<String, String>().add(
            "iter.events.chat",
            mapOf("type" to "PAYMENT_CONFIRMED", "payload" to payload),
        ).awaitSingleOrNull()

        val updated = withTimeout(10_000) {
            var current = chatRoomRepository.findById(room.id!!)
            var pending = reactiveRedisTemplate.opsForStream<String, String>()
                .pending("iter.events.chat", "chat")
                .awaitSingleOrNull()
            while (current?.stage != RoomStage.TRADE || pending?.totalPendingMessages != 0L) {
                delay(200)
                current = chatRoomRepository.findById(room.id)
                pending = reactiveRedisTemplate.opsForStream<String, String>()
                    .pending("iter.events.chat", "chat")
                    .awaitSingleOrNull()
            }
            current
        }

        assertThat(updated.stage).isEqualTo(RoomStage.TRADE)
        assertThat(updated.rentalId).isEqualTo(999L)
        assertThat(
            reactiveRedisTemplate.opsForStream<String, String>()
                .pending("iter.events.chat", "chat")
                .awaitSingleOrNull()
                ?.totalPendingMessages,
        ).isZero()
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
