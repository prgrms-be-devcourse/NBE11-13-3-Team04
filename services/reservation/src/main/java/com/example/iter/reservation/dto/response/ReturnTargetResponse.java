package com.example.iter.reservation.dto.response;

import com.example.iter.auth.api.UserSummary;

import java.time.LocalDate;

public record ReturnTargetResponse(
        Long rentalId,
        String equipmentName,
        String thumbnailUrl,
        UserSummary renter,
        LocalDate endDate,
        LocalDate returnDate
) {
}