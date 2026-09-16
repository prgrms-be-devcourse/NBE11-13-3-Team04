package com.example.iter.dispute.dto.request

import com.example.iter.dispute.domain.entity.ReportStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminReportUpdateRequest(
    @field:NotNull(message = "변경할 신고 상태는 필수입니다.")
    val status: ReportStatus?,

    @field:NotBlank(message = "관리자 처리 메모는 필수입니다.")
    @field:Size(max = 2000, message = "관리자 처리 메모는 2000자 이하여야 합니다.")
    val adminMemo: String?
)
