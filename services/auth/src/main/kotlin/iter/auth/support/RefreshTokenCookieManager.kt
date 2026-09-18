package iter.auth.support

import iter.auth.config.RefreshTokenCookieProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Optional

@Component
class RefreshTokenCookieManager(
    private val properties: RefreshTokenCookieProperties,
) {

    fun create(refreshToken: String): ResponseCookie =
        baseCookie(refreshToken)
            .maxAge(properties.maxAge!!)
            .build()

    fun delete(): ResponseCookie =
        baseCookie("")
            .maxAge(Duration.ZERO)
            .build()

    fun extract(request: HttpServletRequest): Optional<String> {
        val cookies = request.cookies ?: return Optional.empty()

        return cookies.asSequence()
            .filter { cookie -> properties.name == cookie.name }
            .map { it.value }
            .filter { value -> !value.isBlank() }
            .firstOrNull()
            .let { Optional.ofNullable(it) }
    }

    fun getCookieName(): String? = properties.name

    private fun baseCookie(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(properties.name!!, value)
            .httpOnly(properties.isHttpOnly)
            .secure(properties.isSecure)
            .sameSite(properties.sameSite!!)
            .path(properties.path!!)
}
