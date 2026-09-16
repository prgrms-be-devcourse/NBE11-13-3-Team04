package com.example.iter.admin.payment.model

import com.example.iter.payment.api.PaymentStatus
import com.example.iter.reservation.api.RentalStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminPaymentDetailRow(
    val paymentId: Long,
    val rentalId: Long,
    val orderId: String?,
    val renterId: Long,
    val renterEmail: String,
    val renterName: String,
    val renterNickname: String?,
    val equipmentId: Long,
    val equipmentName: String,
    val category: String?,
    val dailyPrice: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rentalDays: Int,
    val totalPrice: BigDecimal,
    val amount: BigDecimal,
    val paymentStatus: PaymentStatus,
    val rentalStatus: RentalStatus,
    val paidAt: LocalDateTime?,
    val refundedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
