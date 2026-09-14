package com.example.iter.reservation.dto.response;

import com.example.iter.reservation.api.RentalStatus;

public record ReturnConfirmationResponse(
        Long rentalId,
        RentalStatus status,
        Long disputeId
) {
}
