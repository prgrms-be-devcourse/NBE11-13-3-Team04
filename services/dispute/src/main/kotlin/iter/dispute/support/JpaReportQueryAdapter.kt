package iter.dispute.support

import iter.dispute.api.ReportQueryPort
import iter.dispute.domain.entity.ReportStatus
import iter.dispute.domain.entity.ReportTargetType
import iter.dispute.domain.repository.ReportRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// dispute/api/ReportQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
//
// "미처리 = RECEIVED" 와 "회원을 향한 신고 = targetType USER" 라는 판단이 여기 있다.
// 이전에는 각각 관리자 대시보드(admin)와 회원 관리(auth) 코드에 박혀 있었다.
@Component
class JpaReportQueryAdapter(
    private val reportRepository: ReportRepository,
) : ReportQueryPort {

    @Transactional(readOnly = true)
    override fun countReceived(): Long =
        reportRepository.countByStatus(ReportStatus.RECEIVED)

    @Transactional(readOnly = true)
    override fun countAgainstUser(userId: Long): Long =
        reportRepository.countByTargetTypeAndTargetId(ReportTargetType.USER, userId)
}
