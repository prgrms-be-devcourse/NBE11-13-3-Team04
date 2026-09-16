package com.example.iter.chatbridge.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatInquiryGrantRequest(
        @NotNull(message = "장비 ID는 필수입니다.")
        Long equipmentId
) {
}
