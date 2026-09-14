package com.example.iter.reservation.dto.response;

import com.example.iter.reservation.api.RentalStatus;

import java.time.LocalDate;

public record ReturnEvidenceCreateResponse(
        Long rentalId,
        RentalStatus status,
        LocalDate returnDate
) {
}
