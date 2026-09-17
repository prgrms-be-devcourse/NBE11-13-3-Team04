package com.example.iter.dispute.service

import com.example.iter.auth.api.UserLockPort
import com.example.iter.auth.api.UserQueryPort
import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.UserStatus
import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportStatus
import com.example.iter.dispute.domain.repository.ReportRepository
import com.example.iter.dispute.domain.repository.spec.ReportSpecifications
import com.example.iter.dispute.dto.request.ReportCreateRequest
import com.example.iter.dispute.dto.request.ReportSearchRequest
import com.example.iter.dispute.dto.response.ReportDetailResponse
import com.example.iter.dispute.dto.response.ReportSummaryResponse
import com.example.iter.dispute.util.ReportMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val userQueryPort: UserQueryPort,
    private val userLockPort: UserLockPort,
    private val reportTargetValidator: ReportTargetValidator,
    private val reportMapper: ReportMapper
) {

    // 신고자 행을 먼저 잠근 뒤 대상 정책과 처리 중 중복 여부를 확인하여 신고를 접수합니다.
    @Transactional
    fun createReport(reporterId: Long, request: ReportCreateRequest): ReportDetailResponse {
        lockAndValidateReporter(reporterId)

        val targetType = requireNotNull(request.targetType)
        val targetId = requireNotNull(request.targetId)

        reportTargetValidator.validate(targetType, targetId, reporterId)
        validateDuplicateActiveReport(reporterId, targetType, targetId)

        val savedReport = reportRepository.save(
            Report(
                reporterId = reporterId,
                targetType = targetType,
                targetId = targetId,
                reason = requireNotNull(request.reason).trim(),
                description = requireNotNull(request.description).trim(),
                status = ReportStatus.RECEIVED,
            )
        )

        log.info(
            "신고 접수 처리: reportId={}, reporterId={}, targetType={}, targetId={}, status={}",
            savedReport.id,
            reporterId,
            savedReport.targetType,
            savedReport.targetId,
            savedReport.status
        )

        val reporter = userQueryPort.findSummary(reporterId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        return reportMapper.toDetail(savedReport, reporter)
    }

    // 본인 신고만 조건 검색하며 기존 외부 계약에 맞춰 오프셋 페이지 응답을 유지합니다.
    @Transactional(readOnly = true)
    fun getMyReports(reporterId: Long, request: ReportSearchRequest): PageResponse<ReportSummaryResponse> {
        val reporter = userQueryPort.findSummary(reporterId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val reports = reportRepository.findAll(
            ReportSpecifications.myReports(reporterId, request.targetType, request.status),
            reportPageable(request.page, request.size)
        ).map { report -> reportMapper.toSummary(report, reporter) }

        return PageResponse.from(reports)
    }

    // reportId와 reporterId를 함께 조회 조건으로 사용해 타인의 신고 존재 여부도 노출하지 않습니다.
    @Transactional(readOnly = true)
    fun getMyReport(reporterId: Long, reportId: Long): ReportDetailResponse {
        val reporter = userQueryPort.findSummary(reporterId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val report = reportRepository.findByIdAndReporterId(reportId, reporterId).orElseThrow { CustomException(ErrorCode.REPORT_NOT_FOUND) }

        return reportMapper.toDetail(report, reporter)
    }

    // 같은 신고자가 보낸 동시 요청을 직렬화하여 애플리케이션 중복 검사 사이의 경쟁을 줄입니다.
    private fun lockAndValidateReporter(reporterId: Long) {
        val reporter = userLockPort.lockAll(listOf(reporterId))[reporterId]
            ?: throw CustomException(ErrorCode.USER_NOT_FOUND)

        when (reporter.status) {
            UserStatus.SUSPENDED -> throw CustomException(ErrorCode.USER_SUSPENDED)
            UserStatus.DELETED -> throw CustomException(ErrorCode.USER_DELETED)
            else -> Unit
        }
    }

    // 접수·검토 중인 같은 대상 신고만 중복으로 판단하고 종결 후 재신고는 허용합니다.
    private fun validateDuplicateActiveReport(reporterId: Long, targetType: com.example.iter.dispute.domain.entity.ReportTargetType, targetId: Long) {
        if (reportRepository.existsActiveReport(reporterId, targetType, targetId, ACTIVE_REPORT_STATUSES)) {
            throw CustomException(ErrorCode.DUPLICATE_ACTIVE_REPORT)
        }
    }

    private fun reportPageable(page: Int, size: Int): Pageable = PageRequest.of(
        page,
        size,
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
    )

    private companion object {
        private val ACTIVE_REPORT_STATUSES = setOf(ReportStatus.RECEIVED, ReportStatus.UNDER_REVIEW)
        private val log = LoggerFactory.getLogger(ReportService::class.java)
    }
}
