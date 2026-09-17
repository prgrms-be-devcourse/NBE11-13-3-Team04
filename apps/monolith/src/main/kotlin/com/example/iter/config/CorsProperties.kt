package com.example.iter.config

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

@Validated
@Component
@ConfigurationProperties(prefix = "app.cors")
class CorsProperties {

    @NotEmpty
    var allowedOrigins: MutableList<String> = mutableListOf()

    @AssertTrue(message = "Credential CORS에서는 와일드카드 Origin을 사용할 수 없습니다.")
    fun isWildcardOriginAbsent(): Boolean = !allowedOrigins.contains("*")
}
