package iter.dispute.util

import iter.auth.api.UserSummary
import iter.dispute.domain.entity.Report
import iter.dispute.dto.response.ReportDetailResponse
import iter.dispute.dto.response.ReportSummaryResponse
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
