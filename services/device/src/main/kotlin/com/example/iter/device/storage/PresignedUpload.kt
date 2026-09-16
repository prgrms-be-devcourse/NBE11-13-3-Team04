package com.example.iter.device.storage

import java.net.URL
import java.time.LocalDateTime

data class PresignedUpload(
    val objectKey: String,
    val uploadUrl: URL,
    val requiredHeaders: Map<String, String>,
    val expiresAt: LocalDateTime,
)
