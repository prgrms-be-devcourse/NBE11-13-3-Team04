package com.example.iter.payment.dto.response;

import com.example.iter.payment.domain.entity.Payment;
import com.example.iter.payment.api.PaymentStatus;
import com.example.iter.reservation.api.RentalInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        Long paymentId,
        Long rentalId,
        Long equipmentId,
        String equipmentName,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        String orderId,
        LocalDateTime paidAt,
        LocalDateTime refundedAt,
        LocalDateTime createdAt
) {
    public static PaymentHistoryResponse of(Payment payment, RentalInfo rental) {
        return new PaymentHistoryResponse(
                payment.getId(),
                rental.rentalId(),
                rental.equipmentId(),
                rental.productName(),
                rental.startDate(),
                rental.endDate(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getOrderId(),
                payment.getPaidAt(),
                payment.getRefundedAt(),
                payment.getCreatedAt()
        );
    }
}
