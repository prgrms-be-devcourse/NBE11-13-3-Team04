package iter.auth.support

import iter.auth.config.OAuth2FlowProperties
import iter.auth.service.OAuthPendingTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.RedirectStrategy
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class KakaoOAuth2SuccessHandler(
    private val pendingTokenService: OAuthPendingTokenService,
    private val userInfoMapper: KakaoOAuth2UserInfoMapper,
    private val properties: OAuth2FlowProperties,
    private val failureHandler: KakaoOAuth2FailureHandler,
    private val exchangeSessionManager: OAuth2ExchangeSessionManager,
) : AuthenticationSuccessHandler {

    private val redirectStrategy: RedirectStrategy = DefaultRedirectStrategy()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        try {
            if (authentication !is OAuth2AuthenticationToken || "kakao" != authentication.authorizedClientRegistrationId) {
                throw IllegalArgumentException("지원하지 않는 OAuth2 인증 제공자입니다.")
            }

            val userInfo = userInfoMapper.map(authentication.principal)
            val exchangeCode = pendingTokenService.issueLoginExchange(userInfo)
            exchangeSessionManager.store(request, exchangeCode)
            val redirectUri = properties.frontendCallbackUri!!

            SecurityContextHolder.clearContext()
            redirectStrategy.sendRedirect(request, response, redirectUri)
        } catch (exception: Exception) {
            failureHandler.onAuthenticationFailure(
                request,
                response,
                OAuth2AuthenticationException(
                    OAuth2Error("kakao_login_processing_failed"),
                    exception,
                ),
            )
        }
    }
}
