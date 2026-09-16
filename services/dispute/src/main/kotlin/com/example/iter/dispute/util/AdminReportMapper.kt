package com.example.iter.dispute.util

import com.example.iter.auth.api.UserSummary
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.dto.response.AdminReportDetailResponse
import com.example.iter.dispute.dto.response.ReportSummaryResponse
import org.springframework.stereotype.Component

@Component
class AdminReportMapper(private val reportMapper: ReportMapper) {
    fun toSummary(report: Report, reporter: UserSummary): ReportSummaryResponse =
        reportMapper.toSummary(report, reporter)

    fun toDetail(report: Report, reporter: UserSummary): AdminReportDetailResponse = AdminReportDetailResponse(
        reportMapper.toDetail(report, reporter),
        report.adminMemo,
        report.updatedAt
    )
}
