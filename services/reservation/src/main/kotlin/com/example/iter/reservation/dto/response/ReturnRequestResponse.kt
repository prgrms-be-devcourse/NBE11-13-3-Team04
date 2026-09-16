package com.example.iter.reservation.dto.response

import com.example.iter.reservation.api.RentalStatus

data class ReturnRequestResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
}
