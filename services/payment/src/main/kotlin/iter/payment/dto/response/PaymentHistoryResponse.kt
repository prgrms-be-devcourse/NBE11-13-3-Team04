package iter.payment.dto.response

import iter.payment.api.PaymentStatus
import iter.payment.domain.entity.Payment
import iter.reservation.api.RentalInfo
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@JvmRecord
data class PaymentHistoryResponse(
    val paymentId: Long?,
    val rentalId: Long,
    val equipmentId: Long,
    val equipmentName: String,
    val rentalStartDate: LocalDate,
    val rentalEndDate: LocalDate,
    val amount: BigDecimal,
    val paymentStatus: PaymentStatus,
    val orderId: String?,
    val paidAt: LocalDateTime?,
    val refundedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(payment: Payment, rental: RentalInfo): PaymentHistoryResponse = PaymentHistoryResponse(
            payment.id,
            rental.rentalId,
            rental.equipmentId,
            rental.productName,
            rental.startDate,
            rental.endDate,
            payment.amount,
            payment.status,
            payment.orderId,
            payment.paidAt,
            payment.refundedAt,
            payment.createdAt,
        )
    }
}
