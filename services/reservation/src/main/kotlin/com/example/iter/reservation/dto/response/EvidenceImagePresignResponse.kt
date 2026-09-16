package com.example.iter.reservation.dto.response

import com.example.iter.common.image.CaptureView
import java.time.LocalDateTime

data class EvidenceImagePresignResponse(
    val uploads: List<Item>,
) {
    fun uploads(): List<Item> = uploads

    data class Item(
        val captureView: CaptureView?,
        val objectKey: String,
        val uploadUrl: String,
        val requiredHeaders: Map<String, String>,
        val viewUrl: String,
        val expiresAt: LocalDateTime,
    ) {
        fun captureView(): CaptureView? = captureView
        fun objectKey(): String = objectKey
        fun uploadUrl(): String = uploadUrl
        fun requiredHeaders(): Map<String, String> = requiredHeaders
        fun viewUrl(): String = viewUrl
        fun expiresAt(): LocalDateTime = expiresAt
    }
}
