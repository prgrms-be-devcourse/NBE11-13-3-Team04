package com.example.iter.reservation.dto.response

import com.example.iter.auth.api.UserSummary
import java.time.LocalDate
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReturnTargetResponse(
    val rentalId: Long,
    val equipmentName: String,
    val thumbnailUrl: String?,
    val renter: UserSummary,
    val endDate: LocalDate,
    val returnDate: LocalDate?
)
