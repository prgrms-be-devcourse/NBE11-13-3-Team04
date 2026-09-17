package com.example.iter.payment.dto.toss

import java.time.LocalDateTime
import java.time.OffsetDateTime

@JvmRecord
data class TossConfirmApiResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val approvedAt: String?,
    val totalAmount: Long,
) {
    fun approvedAtAsLocalDateTime(): LocalDateTime = OffsetDateTime.parse(approvedAt).toLocalDateTime()
}
