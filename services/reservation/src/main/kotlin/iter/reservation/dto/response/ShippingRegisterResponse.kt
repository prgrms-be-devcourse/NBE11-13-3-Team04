package iter.reservation.dto.response

import iter.reservation.api.RentalStatus

import java.time.LocalDateTime

data class ShippingRegisterResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val carrier: String?,
    val trackingNumber: String?,
    val shippedAt: LocalDateTime?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun carrier(): String? = carrier
    fun trackingNumber(): String? = trackingNumber
    fun shippedAt(): LocalDateTime? = shippedAt
}
