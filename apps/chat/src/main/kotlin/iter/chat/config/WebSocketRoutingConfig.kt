package iter.chat.config

import iter.chat.ws.ChatWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

// WebFlux는 @MessageMapping류 애너테이션 라우팅이 없다 — URL과 WebSocketHandler를
// 직접 매핑하는 HandlerMapping을 등록해야 한다. WebSocketHandlerAdapter가 있어야
// DispatcherHandler가 이 매핑 결과를 실제 업그레이드 처리로 넘긴다(둘 다 없으면
// /ws/chat이 그냥 일반 HTTP 요청으로 취급돼 404가 난다).
@Configuration
class WebSocketRoutingConfig(
    private val chatWebSocketHandler: ChatWebSocketHandler,
) {

    @Bean
    fun webSocketHandlerMapping(): SimpleUrlHandlerMapping {
        val mapping = SimpleUrlHandlerMapping()
        mapping.order = -1
        mapping.urlMap = mapOf<String, WebSocketHandler>("/ws/chat" to chatWebSocketHandler)
        return mapping
    }

    @Bean
    fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()
}
