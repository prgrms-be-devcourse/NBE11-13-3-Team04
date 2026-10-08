package iter.auth.dto.request

import jakarta.validation.constraints.NotBlank

@JvmRecord
data class OAuthLinkRequest(
    @field:NotBlank(message = "OAuth 연결 토큰은 필수입니다.")
    val oauthToken: String,
)
