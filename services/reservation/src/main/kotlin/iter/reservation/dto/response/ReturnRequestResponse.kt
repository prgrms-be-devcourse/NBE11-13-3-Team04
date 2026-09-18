package iter.reservation.dto.response

import iter.reservation.api.RentalStatus

data class ReturnRequestResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
}
