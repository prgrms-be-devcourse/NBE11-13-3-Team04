package com.example.iter.dispute.service

import com.example.iter.auth.api.UserQueryPort
import com.example.iter.auth.api.UserSummary
import com.example.iter.common.audit.domain.entity.AdminActionTargetType
import com.example.iter.common.audit.domain.entity.AdminActionType
import com.example.iter.common.audit.service.AdminActionService
import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportStatus
import com.example.iter.dispute.domain.repository.ReportRepository
import com.example.iter.dispute.dto.request.AdminReportSearchRequest
import com.example.iter.dispute.dto.request.AdminReportUpdateRequest
import com.example.iter.dispute.dto.response.AdminReportDetailResponse
import com.example.iter.dispute.dto.response.ReportSummaryResponse
import com.example.iter.dispute.util.AdminReportMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class AdminReportService(
    private val reportRepository: ReportRepository,
    private val userQueryPort: UserQueryPort,
    private val adminActionService: AdminActionService,
    private val adminReportMapper: AdminReportMapper,
    private val clock: Clock
) {

    // 신고 생성 시각과 ID를 커서로 사용하고 신고자 정보는 한 번에 조회해 N+1 쿼리를 방지합니다.
    @Transactional(readOnly = true)
    fun getReports(request: AdminReportSearchRequest): CursorPageResponse<ReportSummaryResponse> {
        val cursorKey = CursorCodec.decode(request.cursor)
        val reports = reportRepository.searchForAdminByCursor(
            request.targetType,
            request.status,
            cursorKey?.createdAt,
            cursorKey?.id,
            PageRequest.of(0, request.size + 1)
        )

        val reporterMap = loadReporters(reports)

        return CursorPageResponse.from(
            reports,
            request.size,
            { report ->
                adminReportMapper.toSummary(
                    report,
                    getRequiredReporter(reporterMap, report.reporterId)
                )
            }
        ) { report -> CursorKey(report.createdAt, report.id) }
    }

    // 관리자 상세 화면에 필요한 신고와 신고자 요약을 조회합니다.
    @Transactional(readOnly = true)
    fun getReport(reportId: Long): AdminReportDetailResponse {
        val report = reportRepository.findById(reportId).orElseThrow { CustomException(ErrorCode.REPORT_NOT_FOUND) }
        val reporter = userQueryPort.findSummary(report.reporterId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        return adminReportMapper.toDetail(report, reporter)
    }

    // 동시 상태 변경을 직렬화하고 신고 변경과 관리자 처리 이력을 같은 트랜잭션에 기록합니다.
    @Transactional
    fun updateReportStatus(adminId: Long, reportId: Long, request: AdminReportUpdateRequest): AdminReportDetailResponse {
        val report = reportRepository.findWithLockById(reportId).orElseThrow { CustomException(ErrorCode.REPORT_NOT_FOUND) }
        val requestedStatus = requireNotNull(request.status)
        val adminMemo = requireNotNull(request.adminMemo).trim()

        validateStatusChange(report.status, requestedStatus)

        val action = toAdminActionType(requestedStatus)

        report.changeStatusByAdmin(requestedStatus, adminMemo, LocalDateTime.now(clock))
        adminActionService.record(
            adminId,
            AdminActionTargetType.REPORT,
            report.id,
            action,
            adminMemo
        )

        reportRepository.flush()
        log.info(
            "관리자 신고 상태 변경 처리: adminId={}, reportId={}, action={}, status={}",
            adminId,
            reportId,
            action,
            report.status
        )

        val reporter = userQueryPort.findSummary(report.reporterId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        return adminReportMapper.toDetail(report, reporter)
    }

    // 접수→검토중, 접수/검토중→처리완료·반려만 허용하며 종결된 신고의 재처리를 차단합니다.
    private fun validateStatusChange(currentStatus: ReportStatus, requestedStatus: ReportStatus) {
        val canStartReview = currentStatus == ReportStatus.RECEIVED &&
            requestedStatus == ReportStatus.UNDER_REVIEW
        val canCloseFromReceived = currentStatus == ReportStatus.RECEIVED &&
            requestedStatus in TERMINAL_STATUSES
        val canCloseFromReview = currentStatus == ReportStatus.UNDER_REVIEW &&
            requestedStatus in TERMINAL_STATUSES

        if (!canStartReview && !canCloseFromReceived && !canCloseFromReview) {
            throw CustomException(ErrorCode.INVALID_REPORT_STATUS_TRANSITION)
        }
    }

    private fun toAdminActionType(requestedStatus: ReportStatus): AdminActionType = when (requestedStatus) {
        ReportStatus.UNDER_REVIEW -> AdminActionType.REVIEW_REPORT
        ReportStatus.RESOLVED -> AdminActionType.RESOLVE_REPORT
        ReportStatus.REJECTED -> AdminActionType.REJECT_REPORT
        ReportStatus.RECEIVED -> throw CustomException(ErrorCode.INVALID_REPORT_STATUS_TRANSITION)
    }

    // 현재 페이지의 신고자 ID를 모아 한 번에 조회한 뒤 응답 변환 시 재사용합니다.
    private fun loadReporters(reports: List<Report>): Map<Long, UserSummary> {
        if (reports.isEmpty()) {
            return emptyMap()
        }

        return userQueryPort.findSummaries(reports.map(Report::getReporterId).distinct())
    }

    private fun getRequiredReporter(reporterMap: Map<Long, UserSummary>, reporterId: Long): UserSummary =
        reporterMap[reporterId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)

    private companion object {
        private val TERMINAL_STATUSES = setOf(ReportStatus.RESOLVED, ReportStatus.REJECTED)
        private val log = LoggerFactory.getLogger(AdminReportService::class.java)
    }
}
