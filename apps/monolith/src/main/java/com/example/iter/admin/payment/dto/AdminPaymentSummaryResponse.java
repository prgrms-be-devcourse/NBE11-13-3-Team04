package com.example.iter.admin.payment.dto;

import com.example.iter.payment.api.PaymentStatus;
import com.example.iter.reservation.api.RentalStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentSummaryResponse(
        Long paymentId,
        Long rentalId,
        String orderId,
        Long renterId,
        String renterEmail,
        String renterName,
        String renterNickname,
        String equipmentName,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        RentalStatus rentalStatus,
        LocalDateTime paidAt,
        LocalDateTime refundedAt,
        LocalDateTime createdAt
) {
}
