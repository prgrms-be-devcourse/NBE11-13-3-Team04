package com.example.iter.auth.dto.response

@JvmRecord
data class OAuthActionRequiredResponse(
    val action: OAuthAction,
    val oauthToken: String,
    val email: String?,
    val nickname: String?,
    val expiresIn: Long,
)
