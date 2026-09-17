package com.example.iter.auth.config

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@Component
@ConfigurationProperties(prefix = "auth.refresh-cookie")
class RefreshTokenCookieProperties {

    @NotBlank
    var name: String? = null

    var isSecure: Boolean = false

    var isHttpOnly: Boolean = false

    @NotBlank
    var sameSite: String? = null

    @NotBlank
    var path: String? = null

    @NotNull
    var maxAge: Duration? = null

    @AssertTrue(message = "__Secure- 접두사가 붙은 Cookie는 secure=true여야 합니다.")
    fun isSecurePrefixValid(): Boolean {
        val name = name
        return name == null || !name.startsWith("__Secure-") || isSecure
    }
}
