package iter.reservation.dto.response

import iter.payment.api.PaymentStatus
import iter.reservation.api.RentalStatus
import iter.reservation.domain.entity.Rental

data class RentalRejectResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val reason: String?,
    val paymentStatus: PaymentStatus?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun reason(): String? = reason
    fun paymentStatus(): PaymentStatus? = paymentStatus

    companion object {
        @JvmStatic
        fun of(rental: Rental, paymentStatus: PaymentStatus?): RentalRejectResponse =
            RentalRejectResponse(rental.id, rental.status, rental.rejectReason, paymentStatus)
    }
}
