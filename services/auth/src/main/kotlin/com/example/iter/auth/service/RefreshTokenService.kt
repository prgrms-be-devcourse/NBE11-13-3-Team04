package com.example.iter.auth.service

import com.example.iter.auth.domain.entity.RefreshToken
import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.RefreshTokenRepository
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.auth.exception.RefreshTokenReuseException
import com.example.iter.auth.service.model.IssuedTokenPair
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.JwtTokenProvider
import com.example.iter.common.security.TokenStatus
import com.example.iter.common.security.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = LoggerFactory.getLogger(RefreshTokenService::class.java)

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val refreshTokenHasher: RefreshTokenHasher,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    fun issueForLogin(user: User): String {
        val userId = user.id
            ?: throw IllegalArgumentException("저장되지 않은 회원에게 Refresh Token을 발급할 수 없습니다.")

        val rawRefreshToken = jwtTokenProvider.generateRefreshToken(user.toAuthUser())
        val refreshToken = RefreshToken.builder()
            .userId(userId)
            .tokenHash(refreshTokenHasher.hash(rawRefreshToken))
            .familyId(UUID.randomUUID().toString())
            .expiresAt(jwtTokenProvider.getExpiration(rawRefreshToken))
            .build()

        refreshTokenRepository.save(refreshToken)
        return rawRefreshToken
    }

    @Transactional(noRollbackFor = [RefreshTokenReuseException::class])
    fun rotate(rawRefreshToken: String): IssuedTokenPair {
        validateRefreshToken(rawRefreshToken)

        val tokenHash = refreshTokenHasher.hash(rawRefreshToken)
        val currentToken = refreshTokenRepository.findWithLockByTokenHash(tokenHash)
            .orElseThrow { CustomException(ErrorCode.INVALID_REFRESH_TOKEN) }

        if (currentToken.userId != jwtTokenProvider.getUserId(rawRefreshToken)) {
            throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        val now = LocalDateTime.now()
        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByFamilyId(currentToken.familyId, now)
            log.warn(
                "Refresh Token 재사용 탐지: userId={}, familyId={}",
                currentToken.userId,
                currentToken.familyId,
            )
            throw RefreshTokenReuseException()
        }
        if (currentToken.isExpired(now)) {
            throw CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        val user = userRepository.findById(currentToken.userId)
            .orElseThrow { CustomException(ErrorCode.INVALID_REFRESH_TOKEN) }
        validateUserStatus(user)

        val newRawRefreshToken = jwtTokenProvider.generateRefreshToken(user.toAuthUser())
        val replacementToken = RefreshToken.builder()
            .userId(user.id!!)
            .tokenHash(refreshTokenHasher.hash(newRawRefreshToken))
            .familyId(currentToken.familyId)
            .expiresAt(jwtTokenProvider.getExpiration(newRawRefreshToken))
            .deviceInfo(currentToken.deviceInfo)
            .build()

        val savedReplacement = refreshTokenRepository.saveAndFlush(replacementToken)
        currentToken.rotateTo(savedReplacement.id, now)

        log.info("인증 토큰 재발급 처리: userId={}", user.id)

        return IssuedTokenPair(
            jwtTokenProvider.generateAccessToken(user.toAuthUser()),
            newRawRefreshToken,
        )
    }

    @Transactional
    fun revoke(authenticatedUserId: Long, rawRefreshToken: String) {
        val tokenHash = refreshTokenHasher.hash(rawRefreshToken)
        refreshTokenRepository.findWithLockByTokenHash(tokenHash)
            .filter { token -> token.userId == authenticatedUserId }
            .filter { token -> !token.isRevoked() }
            .ifPresent { token -> token.revoke(LocalDateTime.now()) }
    }

    @Transactional
    fun revokeAllByUserId(userId: Long) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, LocalDateTime.now())
    }

    private fun validateRefreshToken(rawRefreshToken: String) {
        val status = jwtTokenProvider.validateRefreshToken(rawRefreshToken)
        if (status == TokenStatus.EXPIRED) {
            throw CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED)
        }
        if (status != TokenStatus.VALID) {
            throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
    }

    private fun validateUserStatus(user: User) {
        if (user.status == UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.USER_SUSPENDED)
        }
        if (user.status == UserStatus.DELETED) {
            throw CustomException(ErrorCode.USER_DELETED)
        }
    }
}
