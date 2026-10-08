package iter.reservation.dto.response

import iter.auth.api.UserSummary

import java.time.LocalDate

data class ReturnTargetResponse(
    val rentalId: Long?,
    val equipmentName: String?,
    val thumbnailUrl: String?,
    val renter: UserSummary?,
    val endDate: LocalDate?,
    val returnDate: LocalDate?,
) {
    fun rentalId(): Long? = rentalId
    fun equipmentName(): String? = equipmentName
    fun thumbnailUrl(): String? = thumbnailUrl
    fun renter(): UserSummary? = renter
    fun endDate(): LocalDate? = endDate
    fun returnDate(): LocalDate? = returnDate
}
