package com.example.iter.payment.dto.response;

import com.example.iter.reservation.api.RentalInfo;

import java.math.BigDecimal;

public record PaymentReadyResponse(
        Long rentalId,
        String orderId,
        String orderName,
        BigDecimal amount,
        String clientKey,
        String customerKey
) {
    public static PaymentReadyResponse of( RentalInfo rental, String orderId, BigDecimal amount, String clientKey ) {
        return new PaymentReadyResponse(
                rental.rentalId(),
                orderId,
                rental.productName(),
                amount,
                clientKey,
                "user-" + rental.renterId()
        );
    }

}
