package com.example.iter.common.storage

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@Component
@ConfigurationProperties(prefix = "app.storage.s3")
class S3StorageProperties {

    @NotBlank
    lateinit var bucket: String

    @NotBlank
    lateinit var region: String

    var publicBaseUrl: String? = null

    @NotBlank
    var keyPrefix: String = "equipment"

    var presignedUrlValidity: Duration = Duration.ofMinutes(5)
}
