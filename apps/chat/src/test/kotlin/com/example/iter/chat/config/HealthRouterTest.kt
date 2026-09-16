package com.example.iter.chat.config

import com.example.iter.chat.redis.TicketStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient

// DB·Redis 없이 라우팅만 검증하는 슬라이스 테스트다. HealthRouter를 명시적으로 넘겨서
// (@Configuration 클래스는 @WebFluxTest의 기본 스캔 대상이 아니다) 이 빈만 올린다.
//
// 그런데 @WebFluxTest는 WebFilter 빈을 controllers 인자와 무관하게 항상 다 끌어온다
// (Boot 공식 동작) — TicketAuthWebFilter가 걸려서 TicketStore(→ Redis)까지 필요해진다.
// 이 테스트가 보고 싶은 건 라우팅뿐이라 TicketStore를 목으로 바꿔서 Redis 의존을 끊는다.
@WebFluxTest(HealthRouter::class)
class HealthRouterTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @MockitoBean
    lateinit var ticketStore: TicketStore

    @Test
    fun `GET health는 200과 UP 상태를 반환한다`() {
        webTestClient.get().uri("/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }
}
