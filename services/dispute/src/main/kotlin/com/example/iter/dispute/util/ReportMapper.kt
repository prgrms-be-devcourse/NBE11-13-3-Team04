package com.example.iter.dispute.util

import com.example.iter.auth.api.UserSummary
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.dto.response.ReportDetailResponse
import com.example.iter.dispute.dto.response.ReportSummaryResponse
import org.springframework.stereotype.Component

@Component
class ReportMapper {

    fun toSummary(report: Report, reporter: UserSummary): ReportSummaryResponse = ReportSummaryResponse(
        report.id,
        reporter,
        report.targetType,
        report.targetId,
        report.reason,
        report.status,
        report.createdAt
    )

    fun toDetail(report: Report, reporter: UserSummary): ReportDetailResponse = ReportDetailResponse(
        report.id,
        reporter,
        report.targetType,
        report.targetId,
        report.reason,
        report.description,
        report.status,
        report.createdAt,
        report.resolvedAt
    )
}
