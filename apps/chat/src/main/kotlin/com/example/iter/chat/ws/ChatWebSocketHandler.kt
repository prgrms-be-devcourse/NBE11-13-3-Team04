package com.example.iter.chat.ws

import com.example.iter.chat.redis.RoomBroadcaster
import com.example.iter.chat.security.CHAT_PRINCIPAL_ATTRIBUTE
import com.example.iter.chat.security.ChatPrincipal
import com.example.iter.chat.service.ChatMessageWriteService
import com.example.iter.chat.service.ChatRoomService
import com.example.iter.chat.service.SendResult
import com.example.iter.chat.service.ViolationService
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import tools.jackson.databind.json.JsonMapper

private val log = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)

// 연결: GET /ws/chat?roomId={id} — 인증은 TicketAuthWebFilter가 핸드셰이크 HTTP 요청
// 단계에서 이미 끝내 둔다(?ticket= 쿼리파라미터를 그 필터가 본다). 여기서는 필터가
// exchange attribute에 심어 둔 ChatPrincipal을 HandshakeInfo.attributes로 넘겨받기만
// 한다 — Spring이 업그레이드 시점에 exchange attribute를 그대로 복사해 주기 때문에
// 가능하다. 티켓이 무효였거나 이 방의 참여자가 아니면 여기서 1008로 닫는다.
//
// 전송 경로: 클라 SEND → 정책 검사·마스킹(ChatMessageWriteService, stage=INQUIRY일 때만)
// → DB 저장 → Redis Pub/Sub 발행 → RoomBroadcaster가 이 인스턴스에 붙은 같은 방의 모든
// 세션(보낸 사람 포함)에 전달. 보낸 사람도 Pub/Sub을 거쳐서 받는다 — 경로를 하나로
// 유지해 "내가 보낸 건 즉시 반영, 남이 보낸 건 구독으로"처럼 두 갈래로 나누지 않기 위함이다.
@Component
class ChatWebSocketHandler(
    private val chatRoomService: ChatRoomService,
    private val chatMessageWriteService: ChatMessageWriteService,
    private val violationService: ViolationService,
    private val roomBroadcaster: RoomBroadcaster,
    private val sessionRegistry: SessionRegistry,
    private val jsonMapper: JsonMapper,
) : WebSocketHandler {

    override fun handle(session: WebSocketSession): Mono<Void> {
        val roomId = extractRoomId(session)
        val principal = session.handshakeInfo.attributes[CHAT_PRINCIPAL_ATTRIBUTE] as? ChatPrincipal

        if (roomId == null || principal == null) {
            return session.close(CloseStatus.POLICY_VIOLATION)
        }

        // 주의: mono { }는 null을 담지 못한다 — findParticipant()가 null(참여자 아님)을
        // 반환하면 이 Mono는 그냥 비어서 완료된다. "participant == null"로 분기하려던
        // 코드는 그 분기가 아예 안 불려서 죽은 코드였다(컴파일 경고 "Condition is always
        // false"로 잡음 — TicketAuthWebFilter에서 겪은 것과 같은 함정). "비어 있음"과
        // "참여자 아님"이 이미 같은 신호이므로 switchIfEmpty로 그 경우만 처리한다.
        return mono { chatRoomService.findParticipant(roomId, principal.userId) }
            .flatMap { bindSession(session, roomId, principal) }
            .switchIfEmpty(Mono.defer { session.close(CloseStatus.POLICY_VIOLATION) })
    }

    private fun bindSession(session: WebSocketSession, roomId: Long, principal: ChatPrincipal): Mono<Void> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<WebSocketMessage>()
        val chatSession = ChatSession(session, sink)
        sessionRegistry.register(roomId, chatSession)

        val output = session.send(sink.asFlux())

        // concatMap: 한 세션 안에서는 메시지를 순서대로 하나씩 처리한다(동시에 여러 SEND를
        // 병렬 저장하면 순서가 뒤섞일 수 있다). 방 전체 처리량은 세션 수만큼 자연히 늘어난다.
        val input = session.receive()
            .concatMap { message -> mono { handleIncoming(session, sink, roomId, principal, message) } }
            .doFinally { sink.tryEmitComplete() }

        return output.and(input)
            .doFinally { sessionRegistry.unregister(roomId, chatSession) }
    }

    private suspend fun handleIncoming(
        session: WebSocketSession,
        sink: Sinks.Many<WebSocketMessage>,
        roomId: Long,
        principal: ChatPrincipal,
        raw: WebSocketMessage,
    ) {
        val frame = runCatching { jsonMapper.readValue(raw.payloadAsText, IncomingFrame::class.java) }.getOrNull()
        if (frame == null || frame.type != "SEND" || frame.content.isNullOrBlank()) {
            sink.tryEmitNext(session.textMessage(jsonMapper.writeValueAsString(OutgoingFrame.error("INVALID_FRAME"))))
            return
        }

        when (val result = chatMessageWriteService.send(roomId, principal, frame.content)) {
            is SendResult.Muted -> {
                sink.tryEmitNext(session.textMessage(jsonMapper.writeValueAsString(OutgoingFrame.error("MUTED"))))
            }
            is SendResult.Sent -> {
                val payload = jsonMapper.writeValueAsString(OutgoingFrame.message(result.message))
                runCatching { roomBroadcaster.publish(roomId, payload) }
                    .onFailure { e -> log.error("chat 메시지 발행 실패 roomId={} messageId={}", roomId, result.message.id, e) }

                if (result.violations.isNotEmpty()) {
                    violationService.recordAndEscalate(roomId, principal.userId, result.message.id!!, result.violations)
                }
            }
        }
    }

    private fun extractRoomId(session: WebSocketSession): Long? =
        UriComponentsBuilder.fromUri(session.handshakeInfo.uri).build()
            .queryParams.getFirst("roomId")
            ?.toLongOrNull()
}
