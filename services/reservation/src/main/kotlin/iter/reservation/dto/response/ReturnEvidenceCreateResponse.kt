package iter.reservation.dto.response

import iter.reservation.api.RentalStatus

import java.time.LocalDate

data class ReturnEvidenceCreateResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val returnDate: LocalDate?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun returnDate(): LocalDate? = returnDate
}
