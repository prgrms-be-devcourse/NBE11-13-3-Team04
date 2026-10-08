package iter.reservation.dto.response

import iter.payment.api.PaymentStatus
import iter.reservation.api.RentalStatus
import iter.reservation.domain.entity.Rental

data class RentalCancelResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val paymentStatus: PaymentStatus?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun paymentStatus(): PaymentStatus? = paymentStatus

    companion object {
        @JvmStatic
        fun of(rental: Rental, paymentStatus: PaymentStatus?): RentalCancelResponse =
            RentalCancelResponse(rental.id, rental.status, paymentStatus)
    }
}
