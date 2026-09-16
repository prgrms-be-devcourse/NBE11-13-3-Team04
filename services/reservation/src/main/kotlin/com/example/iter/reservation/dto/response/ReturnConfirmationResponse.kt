package com.example.iter.reservation.dto.response

import com.example.iter.reservation.api.RentalStatus

data class ReturnConfirmationResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val disputeId: Long?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun disputeId(): Long? = disputeId
}
