package iter.admin.payment.dto

import iter.payment.api.PaymentStatus
import iter.reservation.api.RentalStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminPaymentSummaryResponse(
    val paymentId: Long?,
    val rentalId: Long?,
    val orderId: String?,
    val renterId: Long?,
    val renterEmail: String?,
    val renterName: String?,
    val renterNickname: String?,
    val equipmentName: String?,
    val amount: BigDecimal?,
    val paymentStatus: PaymentStatus?,
    val rentalStatus: RentalStatus?,
    val paidAt: LocalDateTime?,
    val refundedAt: LocalDateTime?,
    val createdAt: LocalDateTime?
)
