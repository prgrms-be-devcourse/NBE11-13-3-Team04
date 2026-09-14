package com.example.iter.admin.payment.dto;

import com.example.iter.reservation.api.RentalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminPaymentRentalResponse(
        Long equipmentId,
        String equipmentName,
        String category,
        BigDecimal dailyPrice,
        LocalDate startDate,
        LocalDate endDate,
        Integer rentalDays,
        BigDecimal totalPrice,
        RentalStatus rentalStatus
) {
}
