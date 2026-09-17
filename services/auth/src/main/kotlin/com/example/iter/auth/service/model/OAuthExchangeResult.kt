package com.example.iter.auth.service.model

import com.example.iter.auth.dto.response.OAuthActionRequiredResponse

sealed interface OAuthExchangeResult {

    @JvmRecord
    data class Authenticated(val tokenPair: IssuedTokenPair) : OAuthExchangeResult

    @JvmRecord
    data class ActionRequired(val response: OAuthActionRequiredResponse) : OAuthExchangeResult
}
