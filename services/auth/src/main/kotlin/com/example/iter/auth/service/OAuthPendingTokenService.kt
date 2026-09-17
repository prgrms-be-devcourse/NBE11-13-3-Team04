package com.example.iter.auth.service

import com.example.iter.auth.config.OAuth2FlowProperties
import com.example.iter.auth.domain.entity.OAuthPendingPurpose
import com.example.iter.auth.domain.entity.OAuthPendingToken
import com.example.iter.auth.domain.entity.OAuthProvider
import com.example.iter.auth.domain.repository.OAuthPendingTokenRepository
import com.example.iter.auth.service.model.ConsumedOAuthToken
import com.example.iter.auth.service.model.IssuedOAuthPendingToken
import com.example.iter.auth.service.model.KakaoUserInfo
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

private val SECURE_RANDOM = SecureRandom()

@Service
class OAuthPendingTokenService(
    private val pendingTokenRepository: OAuthPendingTokenRepository,
    private val tokenHasher: OAuthPendingTokenHasher,
    private val properties: OAuth2FlowProperties,
) {

    @Transactional
    fun issueLoginExchange(userInfo: KakaoUserInfo): String =
        issue(
            userInfo,
            OAuthPendingPurpose.LOGIN_EXCHANGE,
            null,
            properties.exchangeTokenValidity!!,
        ).rawToken

    @Transactional
    fun consumeLoginExchange(rawToken: String): ConsumedOAuthToken = consume(rawToken, OAuthPendingPurpose.LOGIN_EXCHANGE)

    @Transactional
    fun issueActionToken(source: ConsumedOAuthToken, targetUserId: Long?): IssuedOAuthPendingToken {
        val userInfo = KakaoUserInfo(source.providerUserId, source.email, source.nickname)
        return issue(
            userInfo,
            OAuthPendingPurpose.SIGNUP_OR_LINK,
            targetUserId,
            properties.actionTokenValidity!!,
        )
    }

    @Transactional
    fun consumeActionToken(rawToken: String): ConsumedOAuthToken = consume(rawToken, OAuthPendingPurpose.SIGNUP_OR_LINK)

    private fun issue(
        userInfo: KakaoUserInfo,
        purpose: OAuthPendingPurpose,
        targetUserId: Long?,
        validity: Duration,
    ): IssuedOAuthPendingToken {
        pendingTokenRepository.deleteExpiredBefore(LocalDateTime.now().minus(properties.pendingTokenRetention!!))
        val rawToken = generateRawToken()
        val pendingToken = OAuthPendingToken.builder()
            .tokenHash(tokenHasher.hash(rawToken))
            .provider(OAuthProvider.KAKAO)
            .providerUserId(userInfo.providerUserId)
            .purpose(purpose)
            .email(userInfo.email)
            .nickname(userInfo.nickname)
            .targetUserId(targetUserId)
            .expiresAt(LocalDateTime.now().plus(validity))
            .build()
        pendingTokenRepository.save(pendingToken)
        return IssuedOAuthPendingToken(rawToken, validity.toSeconds())
    }

    private fun consume(rawToken: String, expectedPurpose: OAuthPendingPurpose): ConsumedOAuthToken {
        val pendingToken: OAuthPendingToken = try {
            pendingTokenRepository.findWithLockByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow { CustomException(ErrorCode.OAUTH_TOKEN_INVALID) }
        } catch (e: IllegalArgumentException) {
            throw CustomException(ErrorCode.OAUTH_TOKEN_INVALID)
        }

        if (pendingToken.purpose != expectedPurpose) {
            throw CustomException(ErrorCode.OAUTH_TOKEN_INVALID)
        }
        if (pendingToken.isUsed()) {
            throw CustomException(ErrorCode.OAUTH_TOKEN_ALREADY_USED)
        }

        val now = LocalDateTime.now()
        if (pendingToken.isExpired(now)) {
            throw CustomException(ErrorCode.OAUTH_TOKEN_EXPIRED)
        }

        val consumedToken = ConsumedOAuthToken(
            pendingToken.provider,
            pendingToken.providerUserId,
            pendingToken.email,
            pendingToken.nickname,
            pendingToken.targetUserId,
        )
        pendingToken.use(now)
        return consumedToken
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
