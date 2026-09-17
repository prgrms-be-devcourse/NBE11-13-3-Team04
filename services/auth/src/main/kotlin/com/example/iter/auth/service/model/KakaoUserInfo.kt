package com.example.iter.auth.service.model

@JvmRecord
data class KakaoUserInfo(
    val providerUserId: String,
    val email: String?,
    val nickname: String?,
)
