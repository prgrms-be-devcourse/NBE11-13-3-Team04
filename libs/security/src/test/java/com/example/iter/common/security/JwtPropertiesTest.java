package com.example.iter.common.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    // 64바이트를 표준 Base64 로 인코딩한 값. 테스트 전용이다.
    private static final String VALID_SECRET_KEY =
            "dGVzdC1vbmx5LWl0ZXItand0LXNlY3JldC1rZXktbXVzdC1iZS1sb25nLWVub3VnaC1mb3ItaHM1MTItc2lnbmluZw==";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("정상 설정은 위반이 없다")
    void validProperties() {
        assertThat(validate(VALID_SECRET_KEY)).isEmpty();
    }

    @Test
    @DisplayName("환경변수가 없어 플레이스홀더가 그대로 남으면 해석 실패로 보고한다")
    void unresolvedPlaceholder() {
        // @ConfigurationProperties 바인딩은 값을 못 찾은 플레이스홀더를 문자열로 남긴다.
        // 그대로 흘러가면 '$'(0x24) 에서 Base64 디코딩이 깨진다.
        Set<ConstraintViolation<JwtProperties>> violations = validate("${JWT_SECRET_KEY}");

        assertThat(propertyPaths(violations)).containsOnly("secretKeyResolved");
        assertThat(messages(violations)).allMatch(message -> message.contains("JWT_SECRET_KEY"));
    }

    @Test
    @DisplayName("URL-safe Base64 는 표준 Base64 가 아니라고 보고한다")
    void urlSafeBase64IsRejected() {
        // 첫 바이트를 0xF8 로 두면 URL-safe 인코딩의 첫 글자가 '-'(0x2d) 가 된다.
        // 표준 Base64 라면 같은 자리에 '+' 가 온다. Base64.getDecoder() 는 '-' 를 거부한다.
        byte[] bytes = new byte[64];
        bytes[0] = (byte) 0xF8;
        String urlSafe = Base64.getUrlEncoder().encodeToString(bytes);

        assertThat(urlSafe).startsWith("-");
        assertThat(propertyPaths(validate(urlSafe))).containsOnly("secretKeyBase64");
    }

    @Test
    @DisplayName("디코딩 결과가 32바이트 미만이면 길이 부족으로 보고한다")
    void tooShortSecretKey() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[31]);

        assertThat(propertyPaths(validate(tooShort))).containsOnly("secretKeyLongEnough");
    }

    @Test
    @DisplayName("디코딩 결과가 정확히 32바이트면 통과한다")
    void exactlyMinimumLengthSecretKey() {
        String exactly32Bytes = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(validate(exactly32Bytes)).isEmpty();
    }

    @Test
    @DisplayName("빈 값은 @NotBlank 하나만 보고한다")
    void blankSecretKey() {
        // 해석/Base64/길이 검증까지 저마다 걸리면 같은 원인으로 메시지가 네 줄 나온다.
        assertThat(propertyPaths(validate(" "))).containsOnly("secretKey");
    }

    private Set<ConstraintViolation<JwtProperties>> validate(String secretKey) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("iter");
        properties.setAudience("iter-api");
        properties.setSecretKey(secretKey);
        properties.setAccessTokenValidity(Duration.ofMinutes(15));
        properties.setRefreshTokenValidity(Duration.ofDays(14));
        return validator.validate(properties);
    }

    private Set<String> propertyPaths(Set<ConstraintViolation<JwtProperties>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private Set<String> messages(Set<ConstraintViolation<JwtProperties>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
