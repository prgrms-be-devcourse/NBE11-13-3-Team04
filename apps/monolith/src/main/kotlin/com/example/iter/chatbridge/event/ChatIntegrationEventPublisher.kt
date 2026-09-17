package com.example.iter.chatbridge.event

import com.example.iter.event.contract.PaymentConfirmedIntegrationEvent
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

private val log = LoggerFactory.getLogger(ChatIntegrationEventPublisher::class.java)

// chat이 소비하는 유일한 통합 이벤트 채널. Redis Stream(XADD)을 쓰는 이유는 Pub/Sub과
// 달리 구독자가 그 순간 안 떠 있어도 유실되지 않기 때문이다(chat이 재시작 중이어도
// 다시 뜨면 이어서 읽는다) — 결제 확정처럼 놓치면 안 되는 이벤트에 맞는 선택이다.
@Component
class ChatIntegrationEventPublisher(
    private val redisTemplate: StringRedisTemplate,
    private val jsonMapper: JsonMapper,
) {

    fun publishPaymentConfirmed(event: PaymentConfirmedIntegrationEvent) {
        try {
            redisTemplate.opsForStream<String, String>().add(
                STREAM_KEY,
                mapOf(
                    "type" to TYPE_PAYMENT_CONFIRMED,
                    "payload" to jsonMapper.writeValueAsString(event),
                ),
            )
        } catch (e: Exception) {
            // 여기서 실패하면 이 이벤트는 유실된다(아웃박스 없음) — 알려진 한계다.
            // rentalId를 로그에 남겨서 필요하면 수동으로 재처리할 수 있게 한다.
            log.error("chat 통합 이벤트 발행 실패 rentalId={}", event.rentalId, e)
        }
    }

    companion object {
        // apps:chat의 소비자 그룹 이름과 반드시 맞아야 한다(ChatIntegrationEventConsumer).
        private const val STREAM_KEY = "iter.events.chat"
        private const val TYPE_PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED"
    }
}
