package com.example.iter.reservation.dto.response

import java.time.LocalDateTime

data class EvidenceImagePresignResponse(
    val uploads: List<Item>,
) {
    fun uploads(): List<Item> = uploads

    data class Item(
        val objectKey: String,
        val uploadUrl: String,
        val requiredHeaders: Map<String, String>,
        val publicUrl: String?,
        val expiresAt: LocalDateTime,
    ) {
        fun objectKey(): String = objectKey
        fun uploadUrl(): String = uploadUrl
        fun requiredHeaders(): Map<String, String> = requiredHeaders
        fun publicUrl(): String? = publicUrl
        fun expiresAt(): LocalDateTime = expiresAt
    }
}
