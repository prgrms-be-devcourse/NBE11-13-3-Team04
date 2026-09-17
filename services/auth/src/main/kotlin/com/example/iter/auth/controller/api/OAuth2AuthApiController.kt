package com.example.iter.auth.controller.api

import com.example.iter.auth.api.PreferredLanguage
import com.example.iter.auth.controller.api.spec.OAuth2AuthApiSpec
import com.example.iter.auth.dto.request.KakaoSignUpRequest
import com.example.iter.auth.dto.response.AccessTokenResponse
import com.example.iter.auth.service.OAuth2AuthService
import com.example.iter.auth.service.model.IssuedTokenPair
import com.example.iter.auth.service.model.OAuthExchangeResult
import com.example.iter.auth.support.OAuth2ExchangeSessionManager
import com.example.iter.auth.support.RefreshTokenCookieManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/v1/auth/oauth2/kakao")
class OAuth2AuthApiController(
    private val oAuth2AuthService: OAuth2AuthService,
    private val refreshTokenCookieManager: RefreshTokenCookieManager,
    private val exchangeSessionManager: OAuth2ExchangeSessionManager,
) : OAuth2AuthApiSpec {

    @PostMapping("/exchange")
    override fun exchange(request: HttpServletRequest): ResponseEntity<*> {
        try {
            val exchangeCode = exchangeSessionManager.consume(request)
            val result = oAuth2AuthService.exchange(exchangeCode)
            if (result is OAuthExchangeResult.Authenticated) {
                return tokenResponse(result.tokenPair, HttpStatus.OK)
            }

            val actionRequired = result as OAuthExchangeResult.ActionRequired
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(actionRequired.response)
        } finally {
            exchangeSessionManager.invalidate(request)
        }
    }

    @PostMapping(value = ["/signup"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    override fun signUp(
        @Valid @RequestBody request: KakaoSignUpRequest,
        locale: Locale,
    ): ResponseEntity<*> {
        val result = oAuth2AuthService.signUp(request, PreferredLanguage.fromLocale(locale))
        if (result is OAuthExchangeResult.Authenticated) {
            return tokenResponse(result.tokenPair, HttpStatus.CREATED)
        }

        val actionRequired = result as OAuthExchangeResult.ActionRequired
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(actionRequired.response)
    }

    private fun tokenResponse(tokenPair: IssuedTokenPair, status: HttpStatus): ResponseEntity<AccessTokenResponse> =
        ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(tokenPair.refreshToken).toString())
            .body(AccessTokenResponse(tokenPair.accessToken))
}
