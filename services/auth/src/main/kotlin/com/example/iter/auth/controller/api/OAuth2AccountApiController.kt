package com.example.iter.auth.controller.api

import com.example.iter.auth.controller.api.spec.OAuth2AccountApiSpec
import com.example.iter.auth.dto.request.OAuthLinkRequest
import com.example.iter.auth.service.OAuth2AuthService
import com.example.iter.common.security.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/oauth2/kakao")
class OAuth2AccountApiController(
    private val oAuth2AuthService: OAuth2AuthService,
) : OAuth2AccountApiSpec {

    @PostMapping("/link")
    override fun link(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody request: OAuthLinkRequest,
    ): ResponseEntity<Void> {
        oAuth2AuthService.link(principal.user.id, request.oauthToken)
        return ResponseEntity.noContent().build()
    }
}
