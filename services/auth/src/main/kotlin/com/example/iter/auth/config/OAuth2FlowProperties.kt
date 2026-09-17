package com.example.iter.auth.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@Component
@ConfigurationProperties(prefix = "app.oauth2")
class OAuth2FlowProperties {

    @NotBlank
    var frontendCallbackUri: String? = null

    @NotNull
    var exchangeTokenValidity: Duration? = null

    @NotNull
    var actionTokenValidity: Duration? = null

    @NotNull
    var pendingTokenRetention: Duration? = null
}
