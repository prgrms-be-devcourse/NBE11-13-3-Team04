package iter.dispute.domain.repository

import iter.dispute.domain.entity.Report
import iter.dispute.domain.entity.ReportStatus
import iter.dispute.domain.entity.ReportTargetType
import jakarta.persistence.LockModeType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface ReportRepository : JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    // 특정 유형과 대상 ID로 접수된 신고 수를 조회합니다.
    fun countByTargetTypeAndTargetId(targetType: ReportTargetType, targetId: Long): Long

    // 관리자 대시보드 통계용으로 특정 처리 상태의 신고 수를 조회합니다.
    fun countByStatus(status: ReportStatus): Long

    // 로그인 사용자가 작성한 특정 신고를 조회합니다.
    fun findByIdAndReporterId(reportId: Long, reporterId: Long): Optional<Report>

    // searchMyReports는 ReportSpecifications로 조립해서 findAll(spec, pageable)로 호출한다
    // (value/countQuery를 손으로 두 벌 유지하지 않기 위해 Specification으로 전환).

    // 같은 사용자가 같은 대상에 접수한 처리 중 신고 ID를 최대 한 건 조회합니다.
    @Query(
        """
        select r.id
        from Report r
        where r.reporterId = :reporterId
          and r.targetType = :targetType
          and r.targetId = :targetId
          and r.status in :statuses
        """,
    )
    fun findActiveReportIds(
        @Param("reporterId") reporterId: Long,
        @Param("targetType") targetType: ReportTargetType,
        @Param("targetId") targetId: Long,
        @Param("statuses") statuses: Collection<ReportStatus>,
        pageable: Pageable,
    ): List<Long>

    // 처리 중 신고 전체를 세지 않고 첫 번째 ID가 발견되면 조회를 종료합니다.
    fun existsActiveReport(
        reporterId: Long,
        targetType: ReportTargetType,
        targetId: Long,
        statuses: Collection<ReportStatus>,
    ): Boolean {
        return findActiveReportIds(
            reporterId,
            targetType,
            targetId,
            statuses,
            PageRequest.of(0, 1),
        ).isNotEmpty()
    }

    // 관리자가 전체 신고를 대상 유형과 처리 상태로 필터링해 조회합니다. 대상 유형이나 처리 상태가 null이면 해당 조건은 적용하지 않습니다.
    @Query(
        """
        select r
        from Report r
        where (:targetType is null or r.targetType = :targetType)
          and (:status is null or r.status = :status)
          and (
                :cursorCreatedAt is null
                or r.createdAt < :cursorCreatedAt
                or (r.createdAt = :cursorCreatedAt and r.id < :cursorId)
              )
        order by r.createdAt desc, r.id desc
        """,
    )
    fun searchForAdminByCursor(
        @Param("targetType") targetType: ReportTargetType?,
        @Param("status") status: ReportStatus?,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<Report>

    // 동일 신고의 상태 변경이 동시에 처리되지 않도록 신고 행을 비관적 쓰기 락으로 조회합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(reportId: Long): Optional<Report>
}
