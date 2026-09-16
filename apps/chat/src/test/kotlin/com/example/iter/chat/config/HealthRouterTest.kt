package com.example.iter.chat.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

// DB·Redis 없이 라우팅만 검증하는 슬라이스 테스트다. HealthRouter를 명시적으로 넘겨서
// (@Configuration 클래스는 @WebFluxTest의 기본 스캔 대상이 아니다) 이 빈만 올린다.
@WebFluxTest(HealthRouter::class)
class HealthRouterTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `GET health는 200과 UP 상태를 반환한다`() {
        webTestClient.get().uri("/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }
}
