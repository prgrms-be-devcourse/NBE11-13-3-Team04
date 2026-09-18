package iter.chatbridge.service

import iter.auth.api.UserQueryPort
import iter.chatbridge.redis.ChatTicketPayload
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

// apps:chat은 JWT 비밀키를 갖지 않는다. 대신 monolith가 로그인 사용자를 확인한 뒤
// 이 짧은 티켓을 발급하고, chat은 Redis에서 그 값만 조회해서 신뢰한다.
//
// SSE의 1회용 티켓(SseTicketService)과 달리 여기는 REST에도 재사용해야 해서
// GET으로 소비하고(삭제하지 않음) TTL로만 만료시킨다 — 1회용으로 막으면
// 재연결·과거 메시지 조회가 매번 새 티켓을 요구하게 되어 비실용적이다.
@Service
class ChatTicketService(
    private val redisTemplate: StringRedisTemplate,
    private val userQueryPort: UserQueryPort,
    private val jsonMapper: JsonMapper,
) {

    fun issue(userId: Long): String {
        val summary = userQueryPort.findSummary(userId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val ticket = UUID.randomUUID().toString()
        val payload = jsonMapper.writeValueAsString(ChatTicketPayload(userId, summary.nickName))
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, payload, TICKET_VALIDITY)
        return ticket
    }

    companion object {
        private const val KEY_PREFIX = "chat:ticket:"
        private val TICKET_VALIDITY: Duration = Duration.ofMinutes(10)
    }
}
