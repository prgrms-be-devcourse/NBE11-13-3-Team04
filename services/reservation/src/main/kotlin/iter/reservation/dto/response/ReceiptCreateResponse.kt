package iter.reservation.dto.response

import iter.reservation.api.RentalStatus

import java.time.LocalDateTime

data class ReceiptCreateResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val receivedAt: LocalDateTime?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun receivedAt(): LocalDateTime? = receivedAt
}
