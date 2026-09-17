package com.example.iter.dispute.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.dispute.domain.entity.ReportStatus
import com.example.iter.dispute.domain.entity.ReportTargetType
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReportDetailResponse(
    val reportId: Long?,
    val reporter: UserSummary,
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: String,
    val description: String?,
    val status: ReportStatus,
    val createdAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?
)
