package iter.composition.ai

import iter.ai.port.ReportAnalysisContextPort
import iter.ai.port.ReportAnalysisTarget
import iter.ai.service.ReportAnalysisContext
import iter.auth.domain.entity.User
import iter.auth.domain.repository.UserRepository
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.common.security.Role
import iter.common.security.UserStatus
import iter.dispute.domain.entity.Report
import iter.dispute.domain.entity.ReportStatus
import iter.dispute.domain.repository.ReportRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

// 관리자 신고 AI가 dispute와 다른 도메인의 내부 구현을 직접 참조하지 않도록 연결합니다.
@Component
class ReportAnalysisContextAdapter(
    private val users: UserRepository,
    private val reports: ReportRepository,
    private val builder: ReportAnalysisContextBuilder
) : ReportAnalysisContextPort {
    // 기존 분석 결과 조회 시 신고 존재 여부만 확인하고 관리자 상태는 다시 제한하지 않습니다.
    override fun requireReport(reportId: Long) {
        findReport(reportId)
    }

    // S3 증빙을 조합하기 전에 관리자 권한과 신고 상태를 락 없이 선검증합니다.
    override fun requireAdminAndReport(adminId: Long, reportId: Long): ReportAnalysisTarget {
        validateAdmin(users.findById(adminId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) })

        return target(findReport(reportId))
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockAdminAndReport(adminId: Long, reportId: Long): ReportAnalysisTarget {
        // 관리자와 신고 순서로 잠가 동시 요청에서도 작업 생성 검증 기준을 고정합니다.
        val admin = users.findWithLockById(adminId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        validateAdmin(admin)

        val report = reports.findWithLockById(reportId).orElseThrow { CustomException(ErrorCode.REPORT_NOT_FOUND) }

        return target(report)
    }

    override fun build(reportId: Long, description: String): ReportAnalysisContext = builder.build(reportId, description)

    private fun findReport(reportId: Long): Report = reports.findById(reportId).orElseThrow { CustomException(ErrorCode.REPORT_NOT_FOUND) }

    private fun validateAdmin(user: User) {
        if (user.role != Role.ADMIN) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }

        when (user.status) {
            UserStatus.SUSPENDED -> throw CustomException(ErrorCode.USER_SUSPENDED)
            UserStatus.DELETED -> throw CustomException(ErrorCode.USER_DELETED)
            UserStatus.ACTIVE -> Unit
        }
    }

    // AI 모듈에는 dispute 엔티티 대신 작업 생성에 필요한 최소 상태만 전달합니다.
    private fun target(report: Report): ReportAnalysisTarget = ReportAnalysisTarget(
        targetType = report.targetType.name,
        status = report.status.name,
        closed = report.status == ReportStatus.RESOLVED || report.status == ReportStatus.REJECTED
    )
}
