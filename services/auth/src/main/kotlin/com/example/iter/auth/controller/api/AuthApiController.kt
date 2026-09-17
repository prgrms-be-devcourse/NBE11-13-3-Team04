package com.example.iter.auth.controller.api

import com.example.iter.auth.api.PreferredLanguage
import com.example.iter.auth.controller.api.spec.AuthApiSpec
import com.example.iter.auth.dto.request.LoginRequest
import com.example.iter.auth.dto.request.SignUpRequest
import com.example.iter.auth.dto.response.AccessTokenResponse
import com.example.iter.auth.dto.response.UserResponse
import com.example.iter.auth.service.AuthService
import com.example.iter.auth.service.model.IssuedTokenPair
import com.example.iter.auth.support.RefreshTokenCookieManager
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.CustomUserDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

// MVP 기능 #1 회원가입/로그인(JWT)
@RestController
@RequestMapping("/api/v1/auth")
class AuthApiController(
    private val authService: AuthService,
    private val refreshTokenCookieManager: RefreshTokenCookieManager,
) : AuthApiSpec {

    @PostMapping("/signup")
    override fun signUp(@Valid @RequestBody request: SignUpRequest, locale: Locale): ResponseEntity<UserResponse> {
        val response = authService.signUp(request, PreferredLanguage.fromLocale(locale))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response)
    }

    @PostMapping("/login")
    override fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AccessTokenResponse> {
        val tokenPair = authService.login(request)
        return withRefreshTokenCookie(tokenPair)
    }

    @PostMapping("/refresh")
    override fun refresh(request: HttpServletRequest): ResponseEntity<AccessTokenResponse> {
        val rawRefreshToken = refreshTokenCookieManager.extract(request)
            .orElseThrow { CustomException(ErrorCode.INVALID_REFRESH_TOKEN) }
        val tokenPair = authService.refresh(rawRefreshToken)
        return withRefreshTokenCookie(tokenPair)
    }

    @PostMapping("/logout")
    override fun logout(
        @AuthenticationPrincipal principal: CustomUserDetails,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        refreshTokenCookieManager.extract(request)
            .ifPresent { token -> authService.logout(principal.user.id, token) }

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.delete().toString())
            .build()
    }

    @GetMapping("/csrf")
    override fun csrf(csrfToken: CsrfToken): ResponseEntity<Void> {
        csrfToken.token
        return ResponseEntity.noContent().build()
    }

    private fun withRefreshTokenCookie(tokenPair: IssuedTokenPair): ResponseEntity<AccessTokenResponse> =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(tokenPair.refreshToken).toString())
            .body(AccessTokenResponse(tokenPair.accessToken))
}
