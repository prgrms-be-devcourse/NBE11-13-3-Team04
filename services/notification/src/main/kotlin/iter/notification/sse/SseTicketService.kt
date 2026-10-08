package iter.notification.sse

import iter.common.security.SseTicketResolver
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// EventSource가 Authorization 헤더를 못 실어 보내는 문제를, 액세스 토큰 원문 대신
// "이 SSE 연결 하나만" 열 수 있는 단발성·초단기 티켓으로 우회한다.
// 액세스 토큰(전체 API 권한, 15분 유효)이 URL/로그에 남는 것과 비교해, 이 티켓은 유출돼도
// SSE 구독 한 번 외엔 아무것도 못 하고 60초 안에, 혹은 쓰이는 즉시 무효화된다.
@Component
class SseTicketService internal constructor(
    private val ticketValidity: Duration,
) : SseTicketResolver {

    // 테스트에서 만료 동작을 실제로 60초 기다리지 않고 검증할 수 있도록 열어둔 생성자(위).
    // 운영에서(Spring이 빈을 만들 때)는 이 기본 생성자로 기본 유효기간을 쓴다.
    constructor() : this(DEFAULT_TICKET_VALIDITY)

    private val tickets = ConcurrentHashMap<String, Ticket>()

    fun issue(userId: Long): String {
        sweepExpiredIfCrowded()
        val value = UUID.randomUUID().toString()
        tickets[value] = Ticket(userId, Instant.now().plus(ticketValidity))
        return value
    }

    override fun consume(ticket: String?): Optional<Long> {
        if (ticket == null) {
            return Optional.empty()
        }
        val found = tickets.remove(ticket) // 조회와 동시에 제거 -> 재사용(replay) 불가
        if (found == null || found.isExpired()) {
            return Optional.empty()
        }
        return Optional.of(found.userId)
    }

    // 별도 스케줄러 없이, 티켓이 너무 많이 쌓였을 때만 이번 발급 요청에 얹혀서 청소한다.
    private fun sweepExpiredIfCrowded() {
        if (tickets.size < MAX_TRACKED_TICKETS) {
            return
        }
        tickets.values.removeIf { it.isExpired() }
    }

    private data class Ticket(val userId: Long, val expiresAt: Instant) {
        fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    }

    companion object {
        private const val MAX_TRACKED_TICKETS = 10_000 // 발급만 하고 안 쓴 티켓이 무한정 쌓이는 걸 막는 상한선
        private val DEFAULT_TICKET_VALIDITY: Duration = Duration.ofSeconds(60)
    }
}
