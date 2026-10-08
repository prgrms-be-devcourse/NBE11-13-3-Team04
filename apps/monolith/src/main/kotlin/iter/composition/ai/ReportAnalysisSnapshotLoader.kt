package iter.composition.ai

import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.dispute.domain.repository.ReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportAnalysisSnapshotLoader(
    private val reports: ReportRepository,
    contributors: List<ReportTargetContextContributor>
) {
    // Spring이 주입한 전략 구현을 신고 대상 유형별로 미리 인덱싱합니다.
    private val contributorsByType = contributors.associateBy(ReportTargetContextContributor::targetType)

    // 하나의 읽기 트랜잭션에서 신고와 연관 도메인 스냅샷을 일관된 기준으로 수집합니다.
    @Transactional(readOnly = true)
    fun load(reportId: Long, reviewedDescription: String): ReportAnalysisDraft {
        val report = reports.findById(reportId).orElseThrow { CustomException(ErrorCode.REPORT_NOT_FOUND) }

        // 분기문 대신 대상 유형별 Contributor 전략에 상세 컨텍스트 구성을 위임합니다.
        val contributor = contributorsByType[report.targetType]
            ?: error("신고 대상 컨텍스트 생성기가 없습니다: ${report.targetType}")

        return ReportAnalysisDraft().also { draft -> contributor.contribute(report, reviewedDescription, draft) }
    }
}
