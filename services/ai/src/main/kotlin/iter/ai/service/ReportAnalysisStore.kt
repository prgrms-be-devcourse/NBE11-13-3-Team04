package iter.ai.service

import iter.ai.config.AiServiceProperties
import iter.ai.domain.entity.ReportAnalysisJob
import iter.ai.domain.repository.ReportAnalysisJobRepository
import iter.ai.dto.AiJobRequest
import iter.ai.port.ReportAnalysisContextPort
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.LinkedHashMap
import java.util.Optional

@Service
class ReportAnalysisStore(
    private val jobs: ReportAnalysisJobRepository,
    private val context: ReportAnalysisContextPort,
    private val properties: AiServiceProperties,
    private val mapper: JsonMapper,
    private val clock: Clock
) {
    // 재조회·재접수에 사용하는 신고별 단일 AI 작업을 확인합니다.
    @Transactional(readOnly = true)
    fun find(reportId: Long): Optional<ReportAnalysisJob> {
        context.requireReport(reportId)
        return jobs.findById(reportId)
    }

    // 새 작업을 만들기 전 관리자 권한과 종결 상태를 선검증하되 기존 작업은 계속 조회할 수 있게 합니다.
    @Transactional(readOnly = true)
    fun findExistingForCreate(adminId: Long, reportId: Long): Optional<ReportAnalysisJob> {
        val target = context.requireAdminAndReport(adminId, reportId)
        val existing = jobs.findById(reportId)

        if (existing.isEmpty && target.closed) {
            throw CustomException(ErrorCode.AI_REPORT_CLOSED)
        }

        return existing
    }

    // 관리자·신고 행을 잠근 상태에서 중복 작업과 일일 한도를 확인하고 검증된 컨텍스트를 고정 저장합니다.
    @Transactional
    fun reserve(adminId: Long, reportId: Long, description: String, analysisContext: ReportAnalysisContext): ReportAnalysisJob {
        val target = context.lockAdminAndReport(adminId, reportId)
        val existing = jobs.findById(reportId)

        // 신고당 최초 요청을 재사용해 동시에 접수돼도 Python 작업 UUID가 둘 이상 생기지 않게 합니다.
        if (existing.isPresent) return existing.get()
        if (target.closed) throw CustomException(ErrorCode.AI_REPORT_CLOSED)

        val now = LocalDateTime.now(clock)

        if (
            jobs.countByAdminIdAndCreatedAtGreaterThanEqual(
                adminId,
                now.toLocalDate().atStartOfDay()
            ) >= properties.dailyReportLimit
        ) {
            throw CustomException(ErrorCode.AI_REPORT_DAILY_LIMIT)
        }

        // 시스템이 확인한 사실과 개인정보를 제거한 공개 문구를 구분해 프롬프트 신뢰 경계를 유지합니다.
        val targetContext = LinkedHashMap<String, Any>()

        targetContext["targetType"] = target.targetType
        targetContext["reportStatus"] = target.status
        targetContext["systemFacts"] = analysisContext.systemFacts
        targetContext["publicContent"] = analysisContext.publicContent

        if (analysisContext.priorConditionAnalysis.isNotEmpty()) {
            targetContext["priorConditionAnalysis"] = analysisContext.priorConditionAnalysis
        }

        val payload = linkedMapOf<String, Any>(
            "reason" to "관리자 신고 검토 요청",
            "description" to description.trim(),
            "targetContext" to targetContext,
            "evidenceImages" to analysisContext.evidenceImages
        )

        val request = AiJobRequest.create(
            AiJobRequest.FeatureType.REPORT_TRIAGE_V1,
            reportId.toString(),
            payload
        )

        return jobs.save(
            ReportAnalysisJob(
                reportId = reportId,
                jobId = requireNotNull(request.jobId).toString(),
                adminId = adminId,
                requestJson = mapper.writeValueAsString(request),
                createdAt = now
            )
        )
    }
}
