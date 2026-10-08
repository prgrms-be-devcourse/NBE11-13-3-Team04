package iter.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

// DB·Redis 연결 여부와 무관하게 "프로세스가 떠 있다"만 확인하는 용도다.
// actuator 를 아직 안 붙여서(CH3 스켈레톤 범위 밖) 직접 라우팅한다.
@Configuration
class HealthRouter {

    @Bean
    fun healthRoutes() = coRouter {
        GET("/health") {
            ServerResponse.status(HttpStatus.OK).bodyValueAndAwait(mapOf("status" to "UP"))
        }
    }
}
