package iter.chat.event

import iter.chat.service.ChatRoomService
import iter.event.contract.PaymentConfirmedIntegrationEvent
import iter.event.contract.RentalCompletedIntegrationEvent
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.stream.StreamReceiver
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

private val log = LoggerFactory.getLogger(ChatIntegrationEventConsumer::class.java)

private const val STREAM_KEY = "iter.events.chat"
private const val CONSUMER_GROUP = "chat"
private const val CONSUMER_NAME = "chat-consumer-1"
private const val TYPE_PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED"
private const val TYPE_RENTAL_COMPLETED = "RENTAL_COMPLETED"

// monolith(ChatIntegrationEventPublisher)가 XADD한 iter.events.chat 스트림을 소비자
// 그룹으로 읽는다. Pub/Sub과 다르게 그 순간 안 듣고 있어도 유실되지 않는다 — chat이
// 재시작하는 동안 결제가 확정돼도 다시 뜨면 이어서 읽는다. ACK는 처리에 성공했을 때만
// 보낸다 — 실패한 레코드는 ACK가 안 나가서 재전달된다(멱등은 ChatRoomService가 보장).
@Component
class ChatIntegrationEventConsumer(
    private val connectionFactory: ReactiveRedisConnectionFactory,
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val chatRoomService: ChatRoomService,
    private val jsonMapper: JsonMapper,
) {

    // 알려진 한계: 구독 Flux를 subscribe()만 해 두고 별도 감시가 없다. RoomBroadcaster의
    // Pub/Sub 구독과 같은 한계다 — 운영 전 재연결/헬스체크 정책을 추가해야 한다.
    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        ensureConsumerGroup()
            .then(Mono.defer { subscribe() })
            .subscribe()
    }

    private fun ensureConsumerGroup(): Mono<Void> =
        redisTemplate.opsForStream<String, String>()
            .createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP)
            // 그룹이 이미 있으면 Redis가 BUSYGROUP 에러를 준다 — 정상 상황이라 무시한다.
            .onErrorResume { e ->
                log.debug("chat 소비자 그룹 생성 스킵(이미 있을 수 있음): {}", e.message)
                Mono.empty()
            }
            .then()

    private fun subscribe(): Mono<Void> =
        StreamReceiver.create(connectionFactory)
            .receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
            )
            .concatMap { record -> mono { handle(record) } }
            .doOnError { e -> log.error("chat 통합 이벤트 구독 스트림 오류", e) }
            .then()

    // internal: 통합 테스트에서 스트림 인프라 없이 이 처리 로직만 단위 테스트하려고 열어 둔다.
    internal suspend fun handle(record: MapRecord<String, String, String>) {
        try {
            process(record)
            redisTemplate.opsForStream<String, String>().acknowledge(CONSUMER_GROUP, record).awaitSingleOrNull()
        } catch (e: Exception) {
            log.error("통합 이벤트 처리 실패 recordId={}", record.id, e)
        }
    }

    private suspend fun process(record: MapRecord<String, String, String>) {
        val type = record.value["type"]
        val payload = record.value["payload"] ?: return

        when (type) {
            TYPE_PAYMENT_CONFIRMED -> {
                val event = jsonMapper.readValue(payload, PaymentConfirmedIntegrationEvent::class.java)
                chatRoomService.markPaymentConfirmed(event.equipmentId, event.renterId, event.rentalId)
            }
            TYPE_RENTAL_COMPLETED -> {
                val event = jsonMapper.readValue(payload, RentalCompletedIntegrationEvent::class.java)
                chatRoomService.markRentalCompleted(event.equipmentId, event.renterId, event.rentalId)
            }
        }
    }
}
