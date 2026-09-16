package com.example.iter.reservation.dto.response

import com.example.iter.reservation.api.RentalStatus
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReturnConfirmationResponse(val rentalId: Long?, val status: RentalStatus?, val disputeId: Long?, val reportId: Long?)
