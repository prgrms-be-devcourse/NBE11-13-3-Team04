package iter.dispute.domain.repository.spec

import iter.dispute.domain.entity.Report
import iter.dispute.domain.entity.ReportStatus
import iter.dispute.domain.entity.ReportTargetType
import org.springframework.data.jpa.domain.Specification

// 로그인 사용자 본인의 신고 목록(searchMyReports) 조회 조건을 조립한다.
// value/countQuery를 손으로 두 벌 유지하지 않기 위해 Specification으로 작성.
object ReportSpecifications {

    @JvmStatic
    fun reporterIs(reporterId: Long): Specification<Report> =
        Specification { root, _, cb -> cb.equal(root.get<Long>("reporterId"), reporterId) }

    @JvmStatic
    fun hasTargetType(targetType: ReportTargetType?): Specification<Report> =
        Specification { root, _, cb ->
            if (targetType == null) null else cb.equal(root.get<ReportTargetType>("targetType"), targetType)
        }

    @JvmStatic
    fun hasStatus(status: ReportStatus?): Specification<Report> =
        Specification { root, _, cb ->
            if (status == null) null else cb.equal(root.get<ReportStatus>("status"), status)
        }

    @JvmStatic
    fun myReports(reporterId: Long, targetType: ReportTargetType?, status: ReportStatus?): Specification<Report> =
        Specification.where(reporterIs(reporterId))
            .and(hasTargetType(targetType))
            .and(hasStatus(status))
}
