package iter.dispute.util

import iter.auth.api.UserSummary
import iter.dispute.domain.entity.Report
import iter.dispute.dto.response.AdminReportDetailResponse
import iter.dispute.dto.response.ReportSummaryResponse
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
