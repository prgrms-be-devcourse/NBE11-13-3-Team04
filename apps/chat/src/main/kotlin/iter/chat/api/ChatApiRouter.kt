package iter.chat.api

import iter.chat.dto.request.CreateRoomRequest
import iter.chat.dto.request.MarkReadRequest
import iter.chat.security.requirePrincipal
import iter.chat.service.ChatMessageService
import iter.chat.service.ChatRoomService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

// HealthRouter와 같은 함수형 라우팅 스타일을 그대로 따른다 — 이 앱은 어노테이션 기반
// @RestController를 안 쓴다(코루틴 suspend fun과 자연스럽게 맞물리는 쪽을 택함).
// 인증은 TicketAuthWebFilter가 먼저 돌아서 principal을 채워 두고, 여기서는
// requirePrincipal()로 꺼내기만 한다 — 없으면 ChatException(TICKET_INVALID)이
// ChatExceptionHandler를 거쳐 401로 나간다.
@Configuration
class ChatApiRouter(
    private val chatRoomService: ChatRoomService,
    private val chatMessageService: ChatMessageService,
) {

    @Bean
    fun chatRoutes() = coRouter {
        "/api/v1/chat/rooms".nest {
            POST("") { request ->
                val principal = request.requirePrincipal()
                val body = request.awaitBody<CreateRoomRequest>()
                val room = chatRoomService.createRoom(principal.userId, body.grantToken)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(mapOf("roomId" to room.id))
            }
            GET("") { request ->
                val principal = request.requirePrincipal()
                ServerResponse.ok().bodyValueAndAwait(chatRoomService.listRooms(principal.userId))
            }
            GET("/{roomId}/messages") { request ->
                request.requirePrincipal()
                val roomId = request.pathVariable("roomId").toLong()
                val cursor = request.queryParam("cursor").map(String::toLong).orElse(null)
                val size = request.queryParam("size").map(String::toInt).orElse(20)
                ServerResponse.ok().bodyValueAndAwait(chatMessageService.getMessages(roomId, cursor, size))
            }
            POST("/{roomId}/read") { request ->
                val principal = request.requirePrincipal()
                val roomId = request.pathVariable("roomId").toLong()
                val body = request.awaitBody<MarkReadRequest>()
                chatRoomService.markRead(principal.userId, roomId, body.lastReadMessageId)
                ServerResponse.noContent().buildAndAwait()
            }
        }
    }
}
