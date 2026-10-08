package iter.auth.service.model

@JvmRecord
data class IssuedTokenPair(
    val accessToken: String,
    val refreshToken: String,
)
