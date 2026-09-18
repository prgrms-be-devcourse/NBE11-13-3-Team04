package iter.chat.redis

import iter.chat.ws.SessionRegistry
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.stereotype.Component

private const val CHANNEL_PREFIX = "chat:room:"
private val log = LoggerFactory.getLogger(RoomBroadcaster::class.java)

// 인스턴스 간 메시지 팬아웃. 방마다 구독을 열고 닫지 않고, 패턴 구독(chat:room:*) 하나를
// 앱 기동 시 딱 한 번 시작해서 채널 이름에서 roomId를 뽑아 SessionRegistry로 넘긴다.
//
// 유실 허용: Pub/Sub은 구독자가 그 순간 안 듣고 있으면 메시지가 그냥 사라진다. 메시지는
// 저장 후에 발행하므로(ChatWebSocketHandler) DB에는 이미 남아 있다 — 재연결한 클라이언트는
// REST 히스토리 조회로 따라잡는다.
@Component
class RoomBroadcaster(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    private val sessionRegistry: SessionRegistry,
) {

    // 알려진 한계: 구독 Flux를 subscribe()만 해 두고 별도 감시가 없다. Redis 연결이
    // 끊기면 재구독 없이 팬아웃이 조용히 멈출 수 있다 — 운영 전 재연결 정책을 추가해야 한다.
    @EventListener(ApplicationReadyEvent::class)
    fun subscribe() {
        listenerContainer.receive(PatternTopic("$CHANNEL_PREFIX*"))
            .doOnNext { message ->
                val roomId = message.channel.removePrefix(CHANNEL_PREFIX).toLongOrNull()
                if (roomId == null) {
                    log.warn("chat 채널 이름에서 roomId를 못 뽑음: {}", message.channel)
                    return@doOnNext
                }
                sessionRegistry.broadcastLocally(roomId, message.message)
            }
            .doOnError { e -> log.error("chat 방 구독 스트림 오류", e) }
            .subscribe()
    }

    suspend fun publish(roomId: Long, payload: String) {
        redisTemplate.convertAndSend(CHANNEL_PREFIX + roomId, payload).awaitSingleOrNull()
    }
}
