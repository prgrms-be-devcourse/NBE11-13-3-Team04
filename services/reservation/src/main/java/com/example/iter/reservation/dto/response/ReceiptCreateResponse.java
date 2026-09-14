package com.example.iter.reservation.dto.response;

import com.example.iter.reservation.api.RentalStatus;

import java.time.LocalDateTime;

public record ReceiptCreateResponse(
        Long rentalId,
        RentalStatus status,
        LocalDateTime receivedAt
) {
}
