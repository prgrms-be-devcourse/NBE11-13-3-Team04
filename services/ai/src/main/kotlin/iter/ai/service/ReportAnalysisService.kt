package iter.ai.service

import iter.ai.client.AiServiceClient
import iter.ai.client.AiServiceException
import iter.ai.domain.entity.ReportAnalysisJob
import iter.ai.dto.AiJobRequest
import iter.ai.dto.ReportAnalysisRequest
import iter.ai.dto.ReportAnalysisResponse
import iter.ai.port.ReportAnalysisContextPort
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Service
class ReportAnalysisService(
    private val store: ReportAnalysisStore,
    private val context: ReportAnalysisContextPort,
    private val client: AiServiceClient,
    private val mapper: JsonMapper
) {
    // 관리자가 검토한 텍스트를 저장한 뒤 신고별 단일 AI 작업을 접수합니다.
    fun create(adminId: Long, reportId: Long, input: ReportAnalysisRequest): ReportAnalysisResponse {
        val existing = store.findExistingForCreate(adminId, reportId)

        if (existing.isPresent) {
            return submit(existing.get())
        }

        // S3 사진 확인은 DB 행 잠금 밖에서 실행해 느린 외부 요청이 transaction을 오래 잡지 않게 합니다.
        val description = requireNotNull(input.description)
        val analysisContext = context.build(reportId, description)
        val saved = store.reserve(adminId, reportId, description, analysisContext)

        return submit(saved)
    }

    // 저장된 최초 신고 요청과 같은 UUID로 접수 여부만 다시 확인합니다.
    fun retry(reportId: Long): ReportAnalysisResponse {
        val saved = store.find(reportId).orElseThrow { CustomException(ErrorCode.ENTITY_NOT_FOUND) }

        return submit(saved)
    }

    // 같은 신고를 재분석하지 않도록 저장된 요청을 그대로 Python AI 서비스에 보냅니다.
    private fun submit(saved: ReportAnalysisJob): ReportAnalysisResponse {
        val request = mapper.readValue(saved.requestJson, AiJobRequest::class.java)

        return try {
            val accepted = client.createJob(request)

            ReportAnalysisResponse(
                accepted.jobId,
                requireNotNull(accepted.status).name,
                null,
                null,
                saved.createdAt
            )
        } catch (exception: AiServiceException) {
            unknown(saved)
        }
    }

    // 신고별 작업을 찾아 Python AI 서비스의 상태와 참고 결과를 반환합니다.
    fun get(reportId: Long): ReportAnalysisResponse {
        val saved = store.find(reportId)

        if (saved.isEmpty) {
            return ReportAnalysisResponse(
                null,
                "NOT_REQUESTED",
                null,
                null,
                null
            )
        }

        val job = saved.get()

        return try {
            val result = client.getJob(UUID.fromString(job.jobId))

            if (result.featureType != AiJobRequest.FeatureType.REPORT_TRIAGE_V1) {
                throw CustomException(ErrorCode.AI_REPORT_UNAVAILABLE)
            }

            ReportAnalysisResponse(
                result.jobId,
                requireNotNull(result.status).name,
                result.result,
                result.errorMessage,
                job.createdAt
            )
        } catch (exception: AiServiceException) {
            if (exception.statusCode == 404) {
                unknown(job)
            } else {
                throw CustomException(ErrorCode.AI_REPORT_UNAVAILABLE)
            }
        }
    }

    // AI 접수 여부가 불명확해도 관리자가 수동 처리를 계속할 수 있는 상태로 반환합니다.
    private fun unknown(saved: ReportAnalysisJob): ReportAnalysisResponse = ReportAnalysisResponse(
        UUID.fromString(saved.jobId),
        "SUBMISSION_UNKNOWN",
        null,
        "AI 접수를 확인하지 못했습니다. 같은 신고로 다시 접수할 수 있으며 기존 수동 처리는 가능합니다.",
        saved.createdAt
    )
}
