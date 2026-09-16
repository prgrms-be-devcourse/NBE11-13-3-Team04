package com.example.iter.device.dto.response

import java.time.LocalDateTime

data class PresignedImageUploadItemResponse(
    val objectKey: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
    val expiresAt: LocalDateTime,
)
