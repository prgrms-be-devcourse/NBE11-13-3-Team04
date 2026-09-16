package com.example.iter.reservation.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.payment.api.PaymentStatus
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental

import java.math.BigDecimal
import java.time.LocalDate

data class RentalReceivedItemResponse(
    val rentalId: Long?,
    val equipmentId: Long?,
    val productNameSnapshot: String?,
    val renter: UserSummary?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val totalPrice: BigDecimal?,
    val paymentStatus: PaymentStatus?,
    val requestMessage: String?,
    val status: RentalStatus?,
) {
    fun rentalId(): Long? = rentalId
    fun equipmentId(): Long? = equipmentId
    fun productNameSnapshot(): String? = productNameSnapshot
    fun renter(): UserSummary? = renter
    fun startDate(): LocalDate? = startDate
    fun endDate(): LocalDate? = endDate
    fun totalPrice(): BigDecimal? = totalPrice
    fun paymentStatus(): PaymentStatus? = paymentStatus
    fun requestMessage(): String? = requestMessage
    fun status(): RentalStatus? = status

    companion object {
        @JvmStatic
        fun of(rental: Rental, renter: UserSummary?, paymentStatus: PaymentStatus?): RentalReceivedItemResponse =
            RentalReceivedItemResponse(
                rental.id,
                rental.equipmentId,
                rental.productNameSnapshot,
                renter,
                rental.startDate,
                rental.endDate,
                rental.totalPrice,
                paymentStatus,
                rental.requestMessage,
                rental.status,
            )
    }
}
