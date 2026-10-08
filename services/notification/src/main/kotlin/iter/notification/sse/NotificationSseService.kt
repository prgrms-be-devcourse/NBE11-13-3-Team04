package iter.notification.sse

import iter.notification.dto.response.NotificationResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class NotificationSseService(
    private val registry: SseEmitterRegistry,
) {

    fun subscribe(userId: Long): SseEmitter {
        val emitter = registry.register(userId)
        // 연결 직후 더미 이벤트를 하나 보내야 일부 프록시/브라우저가 커넥션을 idle로 보고 끊는 걸 방지할 수 있다.
        sendToEmitter(userId, emitter, "connect", "connected")
        return emitter
    }

    fun send(userId: Long, payload: NotificationResponse) {
        for (emitter in registry.get(userId)) {
            sendToEmitter(userId, emitter, "notification", payload)
        }
    }

    private fun sendToEmitter(userId: Long, emitter: SseEmitter, eventName: String, data: Any) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data))
        } catch (e: Exception) {
            // IOException(끊긴 연결에 쓰기 시도) 외에 emitter가 이미 완료된 상태에서 send()를 호출하면 IllegalStateException(unchecked)도 던진다
            // — 둘 다 "이 emitter는 이제 못 쓴다"는 같은 신호라 함께 잡아서 여기서 끝낸다.
            // 여기서 벗어나면 NotificationService.create()까지 전파되어 이미 저장된 Notification 행까지 롤백시킬 수 있다.
            log.debug("SSE emitter 전송 실패로 연결 종료: userId={}", userId)
            registry.remove(userId, emitter)
            emitter.completeWithError(e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(NotificationSseService::class.java)
    }
}
