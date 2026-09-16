package com.example.iter.reservation.dto.response

import com.example.iter.auth.api.UserSummary
import java.time.LocalDate
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReturnComparisonResponse(
    val rentalId: Long?,
    val equipmentName: String?,
    val renter: UserSummary?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val returnDate: LocalDate?,
    val listingImages: List<ConditionEvidenceImageResponse> = emptyList(),
    val receipt: ConditionEvidenceResponse?,
    val returnReceipt: ConditionEvidenceResponse?
)
