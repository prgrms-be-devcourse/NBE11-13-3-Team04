package com.example.iter.common.security

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.util.Base64

// HS256 서명 키의 최소 길이. JwtTokenProvider 가 쓰는 Keys.hmacShaKeyFor 도 같은 값을 요구한다.
private const val MIN_SECRET_KEY_BYTES = 32

private const val UNRESOLVED_PLACEHOLDER_PREFIX = "\${"

// application.yml의 jwt.* 프로퍼티를 바인딩하는 클래스 (실제 값은 환경변수로 주입한다)
//
// secret-key 검증이 세 갈래인 이유:
// 여기서 걸러주지 않으면 잘못된 값이 그대로 JwtTokenProvider.init() 까지 흘러가서
// 'Illegal base64 character 24' 같은 메시지로 터진다. 저 숫자는 10진수가 아니라
// 16진수라서(0x24 = '$') 원인을 읽어내려면 매번 손으로 해독해야 한다.
// 설정이 틀렸다는 사실은 설정을 바인딩하는 이 자리에서, 무엇이 어떻게 틀렸는지까지
// 말해주는 편이 낫다.
//
// 필드를 nullable var(기본값 null)로 두는 이유: lateinit으로 두면 프로퍼티가 아예
// 바인딩되지 않은 경우(값 자체가 없는 경우) @NotBlank가 그 사실을 "위반"으로 보고하기 전에
// 이 클래스의 getter 호출 자체가 UninitializedPropertyAccessException으로 먼저 터진다 —
// 자바 시절엔 그냥 null이었던 것과 달리 Bean Validation이 아예 못 도는 회귀다.
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {

    @NotBlank
    var issuer: String? = null

    @NotBlank
    var audience: String? = null

    @NotBlank
    var secretKey: String? = null

    @NotNull
    var accessTokenValidity: Duration? = null

    @NotNull
    var refreshTokenValidity: Duration? = null

    // @ConfigurationProperties 바인딩은 값을 찾지 못한 플레이스홀더를 에러 없이 문자열 그대로 남긴다.
    // (같은 상황에서 @Value 는 곧바로 실패한다 - 둘의 동작이 다르다.)
    // 그래서 환경변수를 빠뜨리면 secretKey 에 "${JWT_SECRET_KEY}" 라는 문자열이 그대로 들어온다.
    @AssertTrue(message = "jwt.secret-key 가 해석되지 않았습니다. JWT_SECRET_KEY 환경변수를 설정하세요.")
    fun isSecretKeyResolved(): Boolean {
        // null/빈 값은 @NotBlank 가 보고한다. 여기서 또 보고하면 메시지가 두 번 나온다.
        val key = secretKey
        return key == null || key.isBlank() || !key.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)
    }

    // 표준 Base64 만 받는다. URL-safe Base64 는 '+' '/' 자리에 '-' '_' 를 쓰는데,
    // JwtTokenProvider 의 Base64.getDecoder() 는 그 두 글자를 거부한다.
    @AssertTrue(message = "jwt.secret-key 는 표준 Base64 여야 합니다. URL-safe Base64 의 '-' 와 '_' 는 쓸 수 없습니다.")
    fun isSecretKeyBase64(): Boolean {
        if (!hasResolvedSecretKey()) {
            return true
        }
        return decodeSecretKeyOrNull() != null
    }

    @AssertTrue(message = "jwt.secret-key 는 디코딩 후 $MIN_SECRET_KEY_BYTES 바이트 이상이어야 합니다.")
    fun isSecretKeyLongEnough(): Boolean {
        if (!hasResolvedSecretKey()) {
            return true
        }
        val decoded = decodeSecretKeyOrNull()
        // 디코딩 자체가 안 되는 경우는 isSecretKeyBase64 가 보고한다.
        return decoded == null || decoded.size >= MIN_SECRET_KEY_BYTES
    }

    private fun hasResolvedSecretKey(): Boolean {
        val key = secretKey
        return key != null && key.isNotBlank() && !key.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)
    }

    private fun decodeSecretKeyOrNull(): ByteArray? {
        return try {
            Base64.getDecoder().decode(secretKey)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
