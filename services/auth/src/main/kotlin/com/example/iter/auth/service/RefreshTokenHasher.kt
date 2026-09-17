package com.example.iter.auth.service

import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64

@Component
class RefreshTokenHasher {

    fun hash(refreshToken: String): String {
        if (!StringUtils.hasText(refreshToken)) {
            throw IllegalArgumentException("Refresh Token은 필수입니다.")
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashed = digest.digest(refreshToken.toByteArray(StandardCharsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 해시 알고리즘을 사용할 수 없습니다.", e)
        }
    }
}
