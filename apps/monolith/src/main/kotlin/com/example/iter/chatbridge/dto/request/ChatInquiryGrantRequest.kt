package com.example.iter.chatbridge.dto.request

import jakarta.validation.constraints.NotNull

@JvmRecord
data class ChatInquiryGrantRequest(
    @field:NotNull(message = "장비 ID는 필수입니다.")
    val equipmentId: Long?,
)
