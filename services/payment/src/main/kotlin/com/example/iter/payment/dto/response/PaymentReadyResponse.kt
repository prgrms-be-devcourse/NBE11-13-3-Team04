package com.example.iter.payment.dto.response

import com.example.iter.reservation.api.RentalInfo
import java.math.BigDecimal

@JvmRecord
data class PaymentReadyResponse(
    val rentalId: Long,
    val orderId: String,
    val orderName: String,
    val amount: BigDecimal,
    val clientKey: String,
    val customerKey: String,
) {
    companion object {
        @JvmStatic
        fun of(rental: RentalInfo, orderId: String, amount: BigDecimal, clientKey: String): PaymentReadyResponse =
            PaymentReadyResponse(
                rental.rentalId,
                orderId,
                rental.productName,
                amount,
                clientKey,
                "user-" + rental.renterId,
            )
    }
}
