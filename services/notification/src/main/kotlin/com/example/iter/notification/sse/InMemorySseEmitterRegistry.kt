package com.example.iter.notification.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// 단일 인스턴스 배포를 전제로 한 인메모리 구현체.
// 같은 사용자가 여러 탭/기기에서 접속할 수 있어 사용자당 emitter를 리스트로 보관한다.
@Component
class InMemorySseEmitterRegistry : SseEmitterRegistry {

    private val emitters = ConcurrentHashMap<Long, MutableList<SseEmitter>>()

    override fun register(userId: Long): SseEmitter {
        val emitter = SseEmitter(TIMEOUT_MILLIS)
        emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(emitter)

        emitter.onCompletion { remove(userId, emitter) }
        emitter.onTimeout {
            remove(userId, emitter)
            emitter.complete()
        }
        emitter.onError { remove(userId, emitter) }

        return emitter
    }

    override fun remove(userId: Long, emitter: SseEmitter) {
        // get() 다음에 별도로 remove(userId)를 호출하면 그 사이에 register()의 computeIfAbsent가 같은 키에 끼어들어 새 emitter를 추가한 리스트를 통째로 날려버릴 수 있다
        // (리스트가 비었다고 판단한 시점과 실제로 맵에서 지우는 시점 사이의 TOCTOU).
        // computeIfPresent는 같은 키에 대해 register()의 computeIfAbsent와 동일한 락을 타므로 검사와 제거를 원자적으로 묶어준다.
        emitters.computeIfPresent(userId) { _, userEmitters ->
            userEmitters.remove(emitter)
            userEmitters.ifEmpty { null }
        }
    }

    override fun get(userId: Long): List<SseEmitter> = emitters.getOrDefault(userId, listOf())

    companion object {
        private const val TIMEOUT_MILLIS = 30 * 60 * 1000L // 30분 — 그 안에 프론트가 재연결한다고 가정
    }
}
