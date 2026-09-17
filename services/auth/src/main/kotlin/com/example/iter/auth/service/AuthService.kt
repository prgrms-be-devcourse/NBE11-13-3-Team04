package com.example.iter.auth.service

import com.example.iter.auth.api.PreferredLanguage
import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.auth.dto.request.LoginRequest
import com.example.iter.auth.dto.request.SignUpRequest
import com.example.iter.auth.dto.response.UserResponse
import com.example.iter.auth.service.model.IssuedTokenPair
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.JwtTokenProvider
import com.example.iter.common.security.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = LoggerFactory.getLogger(AuthService::class.java)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
) {

    @Transactional
    fun signUp(request: SignUpRequest, preferredLanguage: PreferredLanguage): UserResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw CustomException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val user = User.builder()
            .email(request.email)
            .password(passwordEncoder.encode(request.password))
            .name(request.name)
            .nickname(request.nickname)
            .phone(request.phone)
            .preferredLanguage(preferredLanguage)
            .build()

        val savedUser = userRepository.save(user)
        log.info("회원가입 처리: userId={}", savedUser.id)
        return UserResponse.from(savedUser)
    }

    @Transactional
    fun login(request: LoginRequest): IssuedTokenPair {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { CustomException(ErrorCode.INVALID_CREDENTIALS) }

        if (user.password == null || !passwordEncoder.matches(request.password, user.password)) {
            throw CustomException(ErrorCode.INVALID_CREDENTIALS)
        }

        val tokenPair = issueTokens(user)
        log.info("로그인 처리: userId={}, role={}", user.id, user.role)
        return tokenPair
    }

    fun issueTokens(user: User): IssuedTokenPair {
        validateLoginAllowed(user)
        val accessToken = jwtTokenProvider.generateAccessToken(user.toAuthUser())
        val refreshToken = refreshTokenService.issueForLogin(user)
        return IssuedTokenPair(accessToken, refreshToken)
    }

    fun refresh(rawRefreshToken: String): IssuedTokenPair = refreshTokenService.rotate(rawRefreshToken)

    fun logout(userId: Long, rawRefreshToken: String) {
        refreshTokenService.revoke(userId, rawRefreshToken)
        log.info("로그아웃 처리: userId={}", userId)
    }

    private fun validateLoginAllowed(user: User) {
        if (user.status == UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.USER_SUSPENDED)
        }
        if (user.status == UserStatus.DELETED) {
            throw CustomException(ErrorCode.USER_DELETED)
        }
    }
}
