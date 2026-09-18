package iter.chat.redis

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

private const val KEY_PREFIX = "chat:grant:"

// monolith(POST /api/v1/chat/inquiry-grants)가 써 둔 문의 승인을 GETDEL로 소비한다.
// 방 생성은 딱 한 번만 이 값을 쓰면 되고, 재사용을 막아야 해서 읽으면서 지운다
// (TicketStore와 반대 — 저기는 반복 조회가 필요해서 안 지운다).
@Component
class GrantStore(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val jsonMapper: JsonMapper,
) {
    suspend fun consume(grantToken: String): GrantPayload? {
        val raw = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + grantToken).awaitSingleOrNull() ?: return null
        return runCatching { jsonMapper.readValue(raw, GrantPayload::class.java) }.getOrNull()
    }
}
