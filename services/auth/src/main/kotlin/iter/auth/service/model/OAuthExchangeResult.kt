package iter.auth.service.model

import iter.auth.dto.response.OAuthActionRequiredResponse

sealed interface OAuthExchangeResult {

    @JvmRecord
    data class Authenticated(val tokenPair: IssuedTokenPair) : OAuthExchangeResult

    @JvmRecord
    data class ActionRequired(val response: OAuthActionRequiredResponse) : OAuthExchangeResult
}
