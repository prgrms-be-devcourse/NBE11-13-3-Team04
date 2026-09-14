package com.example.iter.admin.payment.dto;

import java.time.LocalDateTime;

public record AdminPaymentDetailResponse(
        AdminPaymentSummaryResponse payment,
        AdminPaymentRentalResponse rental,
        LocalDateTime updatedAt
) {
}
