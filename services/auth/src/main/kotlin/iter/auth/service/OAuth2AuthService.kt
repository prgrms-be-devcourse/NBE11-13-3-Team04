package iter.auth.service

import iter.auth.api.PreferredLanguage
import iter.auth.domain.entity.OAuthAccount
import iter.auth.domain.entity.User
import iter.auth.domain.repository.OAuthAccountRepository
import iter.auth.domain.repository.UserRepository
import iter.auth.dto.request.KakaoSignUpRequest
import iter.auth.dto.response.OAuthAction
import iter.auth.dto.response.OAuthActionRequiredResponse
import iter.auth.service.model.ConsumedOAuthToken
import iter.auth.service.model.IssuedTokenPair
import iter.auth.service.model.OAuthExchangeResult
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = LoggerFactory.getLogger(OAuth2AuthService::class.java)

@Service
class OAuth2AuthService(
    private val pendingTokenService: OAuthPendingTokenService,
    private val oAuthAccountRepository: OAuthAccountRepository,
    private val userRepository: UserRepository,
    private val authService: AuthService,
) {

    @Transactional
    fun exchange(exchangeCode: String): OAuthExchangeResult {
        val pending = pendingTokenService.consumeLoginExchange(exchangeCode)

        return oAuthAccountRepository
            .findByProviderAndProviderUserId(pending.provider, pending.providerUserId)
            .map { account -> authenticateLinkedUser(account.userId) }
            .orElseGet { requireSignupOrLink(pending) }
    }

    @Transactional
    fun signUp(request: KakaoSignUpRequest, preferredLanguage: PreferredLanguage): OAuthExchangeResult {
        val pending = pendingTokenService.consumeActionToken(request.oauthToken)
        if (pending.targetUserId != null) {
            throw CustomException(ErrorCode.OAUTH_TOKEN_INVALID)
        }
        if (oAuthAccountRepository.findByProviderAndProviderUserId(pending.provider, pending.providerUserId).isPresent) {
            throw CustomException(ErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED)
        }
        val signupEmail = resolveSignupEmail(pending.email, request.email)

        return userRepository.findByEmail(signupEmail)
            .map<OAuthExchangeResult> { existingUser -> issueLinkRequired(pending, signupEmail, existingUser) }
            .orElseGet { completeSignUp(request, preferredLanguage, pending, signupEmail) }
    }

    private fun completeSignUp(
        request: KakaoSignUpRequest,
        preferredLanguage: PreferredLanguage,
        pending: ConsumedOAuthToken,
        signupEmail: String,
    ): OAuthExchangeResult {
        val user: User = try {
            userRepository.saveAndFlush(
                User.builder()
                    .email(signupEmail)
                    .password(null)
                    .name(request.name)
                    .nickname(request.nickname)
                    .phone(request.phone)
                    .preferredLanguage(preferredLanguage)
                    .build(),
            )
        } catch (exception: DataIntegrityViolationException) {
            throw CustomException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        saveOAuthAccount(user.id!!, pending)

        log.info("OAuth 회원가입 처리: userId={}, provider={}", user.id, pending.provider)

        return OAuthExchangeResult.Authenticated(authService.issueTokens(user))
    }

    private fun resolveSignupEmail(kakaoEmail: String?, requestedEmail: String): String {
        if (kakaoEmail == null) {
            return requestedEmail
        }
        if (!kakaoEmail.equals(requestedEmail, ignoreCase = true)) {
            throw CustomException(ErrorCode.OAUTH_EMAIL_MISMATCH)
        }
        return kakaoEmail
    }

    @Transactional
    fun link(authenticatedUserId: Long, oauthToken: String) {
        val pending = pendingTokenService.consumeActionToken(oauthToken)
        if (pending.targetUserId == null || pending.targetUserId != authenticatedUserId) {
            throw CustomException(ErrorCode.OAUTH_LINK_TARGET_MISMATCH)
        }
        if (oAuthAccountRepository.findByProviderAndProviderUserId(pending.provider, pending.providerUserId).isPresent ||
            oAuthAccountRepository.existsByUserIdAndProvider(authenticatedUserId, pending.provider)
        ) {
            throw CustomException(ErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED)
        }

        saveOAuthAccount(authenticatedUserId, pending)
        log.info("OAuth 계정 연결 처리: userId={}, provider={}", authenticatedUserId, pending.provider)
    }

    private fun authenticateLinkedUser(userId: Long): OAuthExchangeResult {
        val user = userRepository.findById(userId)
            .orElseThrow { CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED) }
        val tokenPair = authService.issueTokens(user)
        log.info("OAuth 로그인 처리: userId={}", userId)
        return OAuthExchangeResult.Authenticated(tokenPair)
    }

    private fun requireSignupOrLink(pending: ConsumedOAuthToken): OAuthExchangeResult {
        val existingUser = pending.email?.let { email -> userRepository.findByEmail(email).orElse(null) }
        if (existingUser != null) {
            return issueLinkRequired(pending, pending.email, existingUser)
        }

        val actionToken = pendingTokenService.issueActionToken(pending, null)
        val response = OAuthActionRequiredResponse(
            OAuthAction.SIGNUP_REQUIRED,
            actionToken.rawToken,
            pending.email,
            pending.nickname,
            actionToken.expiresIn,
        )
        return OAuthExchangeResult.ActionRequired(response)
    }

    // 카카오가 이메일 동의를 안 준 경우 pending.email이 null이라 requireSignupOrLink에서는
    // 신규/기존을 판단할 수 없다. signUp() 단계에서 사용자가 입력한 이메일로 뒤늦게 충돌이
    // 발견되면 이 메서드로 동일하게 LINK_REQUIRED 토큰을 재발급한다.
    private fun issueLinkRequired(pending: ConsumedOAuthToken, email: String, existingUser: User): OAuthExchangeResult {
        val reissueSource = ConsumedOAuthToken(pending.provider, pending.providerUserId, email, pending.nickname, null)
        val actionToken = pendingTokenService.issueActionToken(reissueSource, existingUser.id)
        val response = OAuthActionRequiredResponse(
            OAuthAction.LINK_REQUIRED,
            actionToken.rawToken,
            email,
            pending.nickname,
            actionToken.expiresIn,
        )
        return OAuthExchangeResult.ActionRequired(response)
    }

    private fun saveOAuthAccount(userId: Long, pending: ConsumedOAuthToken) {
        try {
            oAuthAccountRepository.saveAndFlush(
                OAuthAccount.builder()
                    .userId(userId)
                    .provider(pending.provider)
                    .providerUserId(pending.providerUserId)
                    .build(),
            )
        } catch (exception: DataIntegrityViolationException) {
            throw CustomException(ErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED)
        }
    }
}
