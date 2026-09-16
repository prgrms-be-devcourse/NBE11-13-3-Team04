package com.example.iter.reservation.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.reservation.api.RentalStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.jvm.JvmRecord

@JvmRecord
data class RentalHistoryResponse(
    val rentalId: Long,
    val equipmentId: Long,
    val equipmentName: String,
    val thumbnailUrl: String?,
    val counterparty: UserSummary,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalPrice: BigDecimal,
    val status: RentalStatus,
    val overdueDays: Int
)
