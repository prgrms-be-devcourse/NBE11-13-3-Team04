package com.example.iter.admin.payment.dto

import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminPaymentDetailResponse(
    val payment: AdminPaymentSummaryResponse?,
    val rental: AdminPaymentRentalResponse?,
    val updatedAt: LocalDateTime?
)
