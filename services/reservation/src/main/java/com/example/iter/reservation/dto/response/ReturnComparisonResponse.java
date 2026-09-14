package com.example.iter.reservation.dto.response;

import com.example.iter.auth.api.UserSummary;

import java.time.LocalDate;

public record ReturnComparisonResponse(
        Long rentalId,
        String equipmentName,
        UserSummary renter,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate returnDate,
        ConditionEvidenceResponse receipt,
        ConditionEvidenceResponse returnReceipt
) {
}
