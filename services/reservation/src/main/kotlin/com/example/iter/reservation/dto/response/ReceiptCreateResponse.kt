package com.example.iter.reservation.dto.response

import com.example.iter.reservation.api.RentalStatus

import java.time.LocalDateTime

data class ReceiptCreateResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val receivedAt: LocalDateTime?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun receivedAt(): LocalDateTime? = receivedAt
}
