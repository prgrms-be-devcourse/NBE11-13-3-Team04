package com.example.iter.auth.service.model

import com.example.iter.auth.domain.entity.OAuthProvider

@JvmRecord
data class ConsumedOAuthToken(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val nickname: String?,
    val targetUserId: Long?,
)
