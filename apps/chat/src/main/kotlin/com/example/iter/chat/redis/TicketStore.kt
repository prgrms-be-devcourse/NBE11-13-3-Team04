package com.example.iter.chat.redis

import com.example.iter.chat.security.ChatPrincipal
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

private const val KEY_PREFIX = "chat:ticket:"

// monolith(POST /api/v1/chat/tickets)가 써 둔 티켓을 읽기만 한다 — 소모하지 않는다.
// REST 요청마다(그리고 WS 연결마다) 검증하므로 지우면 두 번째 요청부터 다 막힌다.
// 만료되면 그냥 null — 프론트가 새 티켓을 받아 재시도한다(액세스 토큰 갱신과 같은 패턴).
@Component
class TicketStore(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val jsonMapper: JsonMapper,
) {
    suspend fun resolve(ticket: String): ChatPrincipal? {
        val raw = redisTemplate.opsForValue().get(KEY_PREFIX + ticket).awaitSingleOrNull() ?: return null
        val payload = runCatching { jsonMapper.readValue(raw, TicketPayload::class.java) }.getOrNull() ?: return null
        return ChatPrincipal(payload.userId, payload.nickname)
    }
}
