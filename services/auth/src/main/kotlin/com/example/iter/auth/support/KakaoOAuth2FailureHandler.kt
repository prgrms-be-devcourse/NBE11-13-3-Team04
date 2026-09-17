package com.example.iter.auth.support

import com.example.iter.auth.config.OAuth2FlowProperties
import com.example.iter.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.RedirectStrategy
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

private val log = LoggerFactory.getLogger(KakaoOAuth2FailureHandler::class.java)

@Component
class KakaoOAuth2FailureHandler(
    private val properties: OAuth2FlowProperties,
) : AuthenticationFailureHandler {

    private val redirectStrategy: RedirectStrategy = DefaultRedirectStrategy()

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        log.warn("카카오 OAuth2 인증 실패: {}", exception.javaClass.simpleName)
        val redirectUri = UriComponentsBuilder
            .fromUriString(properties.frontendCallbackUri!!)
            .queryParam("error", ErrorCode.OAUTH_AUTHENTICATION_FAILED.name)
            .build()
            .encode()
            .toUriString()
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
        redirectStrategy.sendRedirect(request, response, redirectUri)
    }
}
