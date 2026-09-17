package com.example.iter.chat.support

import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.Disposable
import reactor.core.publisher.Sinks
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

// 통합 테스트 전용 최소 WebSocket 클라이언트. 실제 서버(RANDOM_PORT로 뜬 Netty)에
// 접속해서 서버가 실제로 보내는 프레임을 받아야 하므로, 단위 테스트처럼 핸들러를
// 직접 호출하지 않고 reactor-netty 클라이언트로 진짜 커넥션을 맺는다.
//
// 폴링 기반 검증을 쓴다(디자인 문서 7.3 — 고정 sleep 금지). await()가 조건을
// 만족할 때까지 짧게 재시도한다.
class TestWebSocketClient(url: String) {

    private val received = CopyOnWriteArrayList<String>()
    private val outbound = Sinks.many().unicast().onBackpressureBuffer<String>()
    private val connected = CompletableFuture<Unit>()
    private val subscription: Disposable

    init {
        val client = ReactorNettyWebSocketClient()
        subscription = client.execute(URI.create(url)) { session ->
            val send = session.send(outbound.asFlux().map(session::textMessage))
            val receive = session.receive()
                .doOnNext { message -> received += message.payloadAsText }
                .then()
            send.and(receive).doOnSubscribe { connected.complete(Unit) }
        }.subscribe({}, { error -> connected.completeExceptionally(error) })
    }

    fun awaitConnected(timeoutSeconds: Long = 5) {
        connected.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun send(rawJson: String) {
        val result = outbound.tryEmitNext(rawJson)
        check(result.isSuccess) { "WebSocket 테스트 프레임 전송 실패: $result" }
    }

    fun messages(): List<String> = received.toList()

    fun close() {
        outbound.tryEmitComplete()
        subscription.dispose()
    }

    companion object {
        // 연결 자체가 비동기라, 핸드셰이크가 끝나기 전에 send()하면 유실될 수 있다.
        fun await(timeoutMillis: Long = 5_000, intervalMillis: Long = 100, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (!condition()) {
                if (System.currentTimeMillis() > deadline) {
                    throw AssertionError("조건이 ${timeoutMillis}ms 안에 충족되지 않았다")
                }
                Thread.sleep(intervalMillis)
            }
        }
    }
}
