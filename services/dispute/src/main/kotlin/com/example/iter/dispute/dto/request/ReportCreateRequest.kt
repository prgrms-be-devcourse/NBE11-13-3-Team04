package com.example.iter.dispute.dto.request

import com.example.iter.dispute.domain.entity.ReportTargetType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReportCreateRequest(
    @field:NotNull(message = "신고 대상 유형은 필수입니다.")
    val targetType: ReportTargetType?,

    @field:NotNull(message = "신고 대상 ID는 필수입니다.")
    @field:Positive(message = "신고 대상 ID는 양수여야 합니다.")
    val targetId: Long?,

    @field:NotBlank(message = "신고 사유는 필수입니다.")
    @field:Size(max = 50, message = "신고 사유는 50자 이하여야 합니다.")
    val reason: String?,

    @field:NotBlank(message = "신고 내용은 필수입니다.")
    @field:Size(max = 2000, message = "신고 내용은 2000자 이하여야 합니다.")
    val description: String?
)
