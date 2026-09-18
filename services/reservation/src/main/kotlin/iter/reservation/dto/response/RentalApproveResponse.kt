package iter.reservation.dto.response

import iter.reservation.api.RentalStatus
import iter.reservation.domain.entity.Rental

import java.time.LocalDateTime

data class RentalApproveResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val approvedAt: LocalDateTime?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun approvedAt(): LocalDateTime? = approvedAt

    companion object {
        @JvmStatic
        fun from(rental: Rental): RentalApproveResponse =
            RentalApproveResponse(rental.id, rental.status, rental.approvedAt)
    }
}
