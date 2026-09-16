package com.example.iter.reservation.dto.response

import com.example.iter.payment.api.PaymentStatus
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental

data class RentalCancelResponse(
    val rentalId: Long?,
    val status: RentalStatus?,
    val paymentStatus: PaymentStatus?,
) {
    fun rentalId(): Long? = rentalId
    fun status(): RentalStatus? = status
    fun paymentStatus(): PaymentStatus? = paymentStatus

    companion object {
        @JvmStatic
        fun of(rental: Rental, paymentStatus: PaymentStatus?): RentalCancelResponse =
            RentalCancelResponse(rental.id, rental.status, paymentStatus)
    }
}
