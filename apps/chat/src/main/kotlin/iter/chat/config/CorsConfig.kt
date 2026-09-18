package iter.chat.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

// monolith(app.cors.allowed-origins, CorsProperties)와 같은 설정 키를 쓴다.
@Component
@ConfigurationProperties(prefix = "app.cors")
class CorsProperties {
    var allowedOrigins: List<String> = emptyList()
}

// chat은 Spring Security가 없는 순수 WebFlux 앱이라 corsConfigurationSource() 빈이 아니라
// CorsWebFilter를 직접 등록해야 브라우저 preflight(OPTIONS)에 Access-Control-Allow-Origin이
// 붙는다. monolith와 달리 쿠키가 아니라 Authorization 헤더(티켓)로만 인증하므로
// allowCredentials는 켜지 않는다.
@Configuration
class CorsConfig(
    private val corsProperties: CorsProperties,
) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = listOf(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name(),
        )
        configuration.allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return CorsWebFilter(source)
    }
}
