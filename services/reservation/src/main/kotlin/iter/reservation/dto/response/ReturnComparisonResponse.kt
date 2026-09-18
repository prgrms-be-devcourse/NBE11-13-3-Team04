package iter.reservation.dto.response

import iter.auth.api.UserSummary

import java.time.LocalDate

data class ReturnComparisonResponse(
    val rentalId: Long?,
    val equipmentName: String?,
    val renter: UserSummary?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val returnDate: LocalDate?,
    val listingImages: List<ConditionEvidenceImageResponse> = emptyList(),
    val receipt: ConditionEvidenceResponse?,
    val returnReceipt: ConditionEvidenceResponse?,
) {
    fun rentalId(): Long? = rentalId
    fun equipmentName(): String? = equipmentName
    fun renter(): UserSummary? = renter
    fun startDate(): LocalDate? = startDate
    fun endDate(): LocalDate? = endDate
    fun returnDate(): LocalDate? = returnDate
    fun listingImages(): List<ConditionEvidenceImageResponse> = listingImages
    fun receipt(): ConditionEvidenceResponse? = receipt
    fun returnReceipt(): ConditionEvidenceResponse? = returnReceipt
}
