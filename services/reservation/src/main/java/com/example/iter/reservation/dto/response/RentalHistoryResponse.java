package com.example.iter.reservation.dto.response;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.reservation.api.RentalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentalHistoryResponse(
        Long rentalId,
        Long equipmentId,
        String equipmentName,
        String thumbnailUrl,
        UserSummary counterparty,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalPrice,
        RentalStatus status,
        int overdueDays
) {
}
