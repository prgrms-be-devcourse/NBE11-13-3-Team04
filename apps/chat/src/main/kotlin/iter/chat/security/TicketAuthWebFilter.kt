package iter.chat.security

import iter.chat.redis.TicketStore
import kotlinx.coroutines.reactor.mono
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

const val CHAT_PRINCIPAL_ATTRIBUTE = "chatPrincipal"

// REST는 Authorization: Bearer {ticket}, WebSocket 핸드셰이크는 헤더를 못 보내는 클라이언트
// (EventSource와 같은 사정)를 대비해 ?ticket= 쿼리 파라미터도 받는다(CH5에서 실제로 쓴다).
// 여기서 인증 실패로 요청을 막지는 않는다 — 방을 안 쓰는 엔드포인트(health 등)까지
// 다 티켓을 요구하게 되므로, "principal이 필요한데 없다"는 각 핸들러가 판단한다
// (ChatRequestExtensions.requirePrincipal 참고).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TicketAuthWebFilter(
    private val ticketStore: TicketStore,
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val ticket = extractTicket(exchange) ?: return chain.filter(exchange)

        // 주의: mono { }가 만드는 Mono는 null을 담지 못한다 — resolve()가 null(무효/만료
        // 티켓)을 반환하면 이 Mono는 그냥 "비어서 완료"된다. flatMap을 쓰면 빈 Mono에서는
        // 람다 자체가 안 불려서 chain.filter(exchange)가 호출되지 않고 요청이 그대로
        // 끊긴다(실제로 이 버그로 작성했다가 컴파일 경고로 잡음) — then()으로 "값이 있든
        // 없든 다음 단계로 진행"을 보장한다.
        return mono { ticketStore.resolve(ticket) }
            .doOnNext { principal -> exchange.attributes[CHAT_PRINCIPAL_ATTRIBUTE] = principal }
            .then(Mono.defer { chain.filter(exchange) })
    }

    private fun extractTicket(exchange: ServerWebExchange): String? {
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ")) {
            return header.removePrefix("Bearer ").trim()
        }
        return exchange.request.queryParams.getFirst("ticket")
    }
}
