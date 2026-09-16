package com.example.iter.chat.event

import com.example.iter.chat.service.ChatRoomService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.ReactiveStreamOperations
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

// start()/subscribe()는 실제 StreamReceiver(Redis 필요)라 여기서는 다루지 않는다 —
// Testcontainers 통합 테스트 대상이다. 여기서는 handle()의 처리 로직(역직렬화 →
// ChatRoomService 호출 → ACK, 실패 시 ACK 안 보냄)만 본다.
@ExtendWith(MockitoExtension::class)
class ChatIntegrationEventConsumerTest {

    @Mock
    private lateinit var connectionFactory: ReactiveRedisConnectionFactory

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var streamOperations: ReactiveStreamOperations<String, String, String>

    @Mock
    private lateinit var chatRoomService: ChatRoomService

    @Spy
    private val jsonMapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    @InjectMocks
    private lateinit var consumer: ChatIntegrationEventConsumer

    @Test
    fun `PAYMENT_CONFIRMED 레코드를 받으면 방을 전환하고 ACK한다`() = runTest {
        whenever(redisTemplate.opsForStream<String, String>()).thenReturn(streamOperations)
        whenever(streamOperations.acknowledge(eq("chat"), any<MapRecord<String, String, String>>())).thenReturn(Mono.just(1L))

        val record = MapRecord.create(
            "iter.events.chat",
            mapOf(
                "type" to "PAYMENT_CONFIRMED",
                "payload" to """{"eventId":"e1","rentalId":1,"equipmentId":2,"renterId":3,"ownerId":4,"occurredAt":"2026-01-01T00:00:00Z"}""",
            ),
        )

        consumer.handle(record)

        verify(chatRoomService).markPaymentConfirmed(2L, 3L, 1L)
        verify(streamOperations).acknowledge(eq("chat"), any<MapRecord<String, String, String>>())
    }

    @Test
    fun `모르는 type은 무시하지만 그대로 ACK한다`() = runTest {
        whenever(redisTemplate.opsForStream<String, String>()).thenReturn(streamOperations)
        whenever(streamOperations.acknowledge(eq("chat"), any<MapRecord<String, String, String>>())).thenReturn(Mono.just(1L))

        val record = MapRecord.create("iter.events.chat", mapOf("type" to "SOMETHING_ELSE", "payload" to "{}"))

        consumer.handle(record)

        verify(chatRoomService, never()).markPaymentConfirmed(any(), any(), any())
        verify(streamOperations).acknowledge(eq("chat"), any<MapRecord<String, String, String>>())
    }

    @Test
    fun `처리 중 실패하면 ACK하지 않아서 재전달되게 한다`() = runTest {
        whenever(chatRoomService.markPaymentConfirmed(any(), any(), any())).thenThrow(RuntimeException("db down"))

        val record = MapRecord.create(
            "iter.events.chat",
            mapOf(
                "type" to "PAYMENT_CONFIRMED",
                "payload" to """{"eventId":"e1","rentalId":1,"equipmentId":2,"renterId":3,"ownerId":4,"occurredAt":"2026-01-01T00:00:00Z"}""",
            ),
        )

        consumer.handle(record)

        verify(redisTemplate, never()).opsForStream<String, String>()
    }
}
