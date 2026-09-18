package iter.chat.ws

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Sinks

// WebFlux의 WebSocketHandler는 "이 세션에 뭘 보낼지"를 세션 생명주기가 시작될 때
// Flux 하나로 딱 한 번 넘겨야 하는 구조다(session.send(flux)). Redis 구독 콜백처럼
// 세션 생명주기 밖에서 비동기로 들어오는 메시지를 그 Flux에 밀어넣으려면 중간에
// 버퍼(Sinks.Many)가 필요하다 — 그래서 세션을 직접 들고 있지 않고 이 Sink를 들고 있는다.
//
// 인스턴스 로컬 레지스트리다. 다른 인스턴스에 붙은 세션은 안 보인다 — 그건
// RoomBroadcaster가 Redis Pub/Sub로 이 레지스트리에까지 이벤트를 전달해서 메운다.
class ChatSession(val session: WebSocketSession, val sink: Sinks.Many<WebSocketMessage>)

@Component
class SessionRegistry {

    private val sessionsByRoom = ConcurrentHashMap<Long, MutableSet<ChatSession>>()

    fun register(roomId: Long, chatSession: ChatSession) {
        sessionsByRoom.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(chatSession)
    }

    fun unregister(roomId: Long, chatSession: ChatSession) {
        sessionsByRoom[roomId]?.remove(chatSession)
    }

    // Redis 구독 콜백(RoomBroadcaster)에서 호출한다 — 이 인스턴스에 붙어 있는 이 방의
    // 세션에만 전달한다. sink가 닫혀 있으면(연결 종료 경합) tryEmit이 조용히 실패만 하고
    // 예외를 던지지 않는다 — 한 세션 실패가 다른 세션 전달을 막으면 안 되므로 그대로 둔다.
    fun broadcastLocally(roomId: Long, payload: String) {
        val sessions = sessionsByRoom[roomId] ?: return
        sessions.forEach { chatSession ->
            chatSession.sink.tryEmitNext(chatSession.session.textMessage(payload))
        }
    }
}
