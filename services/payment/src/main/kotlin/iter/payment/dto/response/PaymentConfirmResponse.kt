package iter.payment.dto.response

import iter.payment.api.PaymentStatus
import iter.payment.domain.entity.Payment
import iter.reservation.api.RentalInfo
import iter.reservation.api.RentalStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@JvmRecord
data class PaymentConfirmResponse(
    val rentalId: Long,
    val paymentId: Long?,
    val paymentKey: String?,
    val amount: BigDecimal,
    val paymentStatus: PaymentStatus,
    val paidAt: LocalDateTime?,
    val rentalStatus: RentalStatus,
) {
    companion object {
        @JvmStatic
        fun of(rental: RentalInfo, payment: Payment): PaymentConfirmResponse = PaymentConfirmResponse(
            rental.rentalId,
            payment.id,
            payment.paymentKey,
            payment.amount,
            payment.status,
            payment.paidAt,
            rental.status,
        )
    }
}
