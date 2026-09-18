package iter.config

import iter.auth.support.KakaoOAuth2FailureHandler
import iter.auth.support.KakaoOAuth2SuccessHandler
import iter.auth.support.NoOpOAuth2AuthorizedClientRepository
import iter.common.exception.ErrorCode
import iter.common.response.ErrorResponse
import iter.common.security.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.json.JsonMapper

// REST API 전용 Stateless 시큐리티 설정.
// (기획서 DoD "JWT 기반 인증/인가 기능 정상 동작 (Role + 소유권 기반)" 대응)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, proxyTargetClass = true) // Spec 인터페이스 구현 컨트롤러도 클래스 기반 프록시로 인가를 적용
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jsonMapper: JsonMapper,
    private val corsProperties: CorsProperties,
    private val kakaoOAuth2SuccessHandler: KakaoOAuth2SuccessHandler,
    private val kakaoOAuth2FailureHandler: KakaoOAuth2FailureHandler,
    private val noOpOAuth2AuthorizedClientRepository: NoOpOAuth2AuthorizedClientRepository,
) {

    @Bean
    @Order(1)
    fun oauth2FilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/oauth2/**", "/login/oauth2/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .securityContext { it.securityContextRepository(NullSecurityContextRepository()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .oauth2Login {
                it.authorizedClientRepository(noOpOAuth2AuthorizedClientRepository)
                    .successHandler(kakaoOAuth2SuccessHandler)
                    .failureHandler(kakaoOAuth2FailureHandler)
            }
        return http.build()
    }

    @Bean
    @Order(2)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()

        http
            .csrf {
                it.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(
                        OrRequestMatcher(
                            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
                            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout"),
                            PathPatternRequestMatcher.pathPattern(
                                HttpMethod.POST,
                                "/api/v1/auth/oauth2/kakao/exchange",
                            ),
                            PathPatternRequestMatcher.pathPattern(
                                HttpMethod.POST,
                                "/api/v1/auth/oauth2/kakao/signup",
                            ),
                        ),
                    )
            }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(*PERMIT_ALL_PATHS).permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/devices",
                        "/api/v1/devices/*",
                        "/api/v1/devices/*/availability",
                        "/api/v1/devices/*/estimate",
                    ).permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exception ->
                exception
                    .authenticationEntryPoint(authenticationEntryPoint()) // 401 - 인증 안 됨
                    .accessDeniedHandler(accessDeniedHandler()) // 403 - 권한/소유권 없음
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            "X-XSRF-TOKEN",
        )
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    // ADMIN은 USER 권한도 포함 (관리자가 일반 회원용 API도 호출 가능하도록)
    @Bean
    fun roleHierarchy(): RoleHierarchy = RoleHierarchyImpl.withDefaultRolePrefix()
        .role("ADMIN").implies("USER")
        .build()

    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ -> writeErrorResponse(response, ErrorCode.UNAUTHORIZED) }

    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { _, response, _ -> writeErrorResponse(response, ErrorCode.FORBIDDEN) }

    private fun writeErrorResponse(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(
            jsonMapper.writeValueAsString(
                ErrorResponse.from(errorCode.name, errorCode.message),
            ),
        )
    }

    companion object {
        // 인증 없이 접근 가능한 경로.
        private val PERMIT_ALL_PATHS = arrayOf(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/csrf",
            "/api/v1/auth/oauth2/kakao/exchange",
            "/api/v1/auth/oauth2/kakao/signup",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/error",
            // 토스 서버가 직접 호출하는 웹훅 — 우리 JWT를 실어보내지 않으므로 인증 대상에서 제외
            "/api/v1/webhooks/toss",
        )
    }
}
