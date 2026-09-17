package com.example.iter.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "toss")
data class TossProperties(val clientKey: String, val secretKey: String)
