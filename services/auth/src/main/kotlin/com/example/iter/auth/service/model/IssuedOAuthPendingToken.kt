package com.example.iter.auth.service.model

@JvmRecord
data class IssuedOAuthPendingToken(
    val rawToken: String,
    val expiresIn: Long,
)
