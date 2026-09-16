package com.example.iter.common.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

// application.yml의 jwt.* 프로퍼티를 바인딩하는 클래스 (실제 값은 환경변수로 주입한다)
//
// secret-key 검증이 세 갈래인 이유:
// 여기서 걸러주지 않으면 잘못된 값이 그대로 JwtTokenProvider.init() 까지 흘러가서
// 'Illegal base64 character 24' 같은 메시지로 터진다. 저 숫자는 10진수가 아니라
// 16진수라서(0x24 = '$') 원인을 읽어내려면 매번 손으로 해독해야 한다.
// 설정이 틀렸다는 사실은 설정을 바인딩하는 이 자리에서, 무엇이 어떻게 틀렸는지까지
// 말해주는 편이 낫다.
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // HS256 서명 키의 최소 길이. JwtTokenProvider 가 쓰는 Keys.hmacShaKeyFor 도 같은 값을 요구한다.
    private static final int MIN_SECRET_KEY_BYTES = 32;

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    @NotBlank
    private String issuer;

    @NotBlank
    private String audience;

    @NotBlank
    private String secretKey;

    @NotNull
    private Duration accessTokenValidity;

    @NotNull
    private Duration refreshTokenValidity;

    // @ConfigurationProperties 바인딩은 값을 찾지 못한 플레이스홀더를 에러 없이 문자열 그대로 남긴다.
    // (같은 상황에서 @Value 는 곧바로 실패한다 - 둘의 동작이 다르다.)
    // 그래서 환경변수를 빠뜨리면 secretKey 에 "${JWT_SECRET_KEY}" 라는 문자열이 그대로 들어온다.
    @AssertTrue(message = "jwt.secret-key 가 해석되지 않았습니다. JWT_SECRET_KEY 환경변수를 설정하세요.")
    public boolean isSecretKeyResolved() {
        // null/빈 값은 @NotBlank 가 보고한다. 여기서 또 보고하면 메시지가 두 번 나온다.
        return secretKey == null
                || secretKey.isBlank()
                || !secretKey.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX);
    }

    // 표준 Base64 만 받는다. URL-safe Base64 는 '+' '/' 자리에 '-' '_' 를 쓰는데,
    // JwtTokenProvider 의 Base64.getDecoder() 는 그 두 글자를 거부한다.
    @AssertTrue(message = "jwt.secret-key 는 표준 Base64 여야 합니다. URL-safe Base64 의 '-' 와 '_' 는 쓸 수 없습니다.")
    public boolean isSecretKeyBase64() {
        if (!hasResolvedSecretKey()) {
            return true;
        }
        return decodeSecretKeyOrNull() != null;
    }

    @AssertTrue(message = "jwt.secret-key 는 디코딩 후 " + MIN_SECRET_KEY_BYTES + "바이트 이상이어야 합니다.")
    public boolean isSecretKeyLongEnough() {
        if (!hasResolvedSecretKey()) {
            return true;
        }
        byte[] decoded = decodeSecretKeyOrNull();
        // 디코딩 자체가 안 되는 경우는 isSecretKeyBase64 가 보고한다.
        return decoded == null || decoded.length >= MIN_SECRET_KEY_BYTES;
    }

    private boolean hasResolvedSecretKey() {
        return secretKey != null
                && !secretKey.isBlank()
                && !secretKey.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX);
    }

    private byte[] decodeSecretKeyOrNull() {
        try {
            return Base64.getDecoder().decode(secretKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
