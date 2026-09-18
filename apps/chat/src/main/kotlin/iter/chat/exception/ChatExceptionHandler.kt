package iter.chat.exception

import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

data class ChatErrorResponse(val code: String, val message: String)

// monolith의 GlobalExceptionHandler(@RestControllerAdvice)와 같은 역할이지만, 여기는
// 어노테이션 기반 컨트롤러가 없는 순수 함수형 라우팅이라 @ExceptionHandler를 못 쓴다.
// WebExceptionHandler가 WebFlux에서 그 대체 SPI다 — Boot가 컨텍스트의 모든
// WebExceptionHandler 빈을 order대로 체인에 끼워 넣는다. 기본 핸들러(500 처리)보다
// 먼저 걸리게 더 앞선 순서를 준다.
@Component
@Order(-2)
class ChatExceptionHandler(
    private val jsonMapper: JsonMapper,
) : WebExceptionHandler {

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val chatException = ex as? ChatException ?: return Mono.error(ex)

        val response = ChatErrorResponse(chatException.errorCode.name, chatException.errorCode.message)
        exchange.response.statusCode = chatException.errorCode.status
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON

        val bytes = jsonMapper.writeValueAsBytes(response)
        val buffer = exchange.response.bufferFactory().wrap(bytes)
        return exchange.response.writeWith(Mono.just(buffer))
    }
}
