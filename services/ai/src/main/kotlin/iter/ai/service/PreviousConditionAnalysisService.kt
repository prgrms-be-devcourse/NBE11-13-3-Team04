package iter.ai.service

import iter.ai.client.AiServiceClient
import iter.ai.client.AiServiceException
import iter.ai.domain.repository.ConditionAnalysisJobRepository
import iter.ai.dto.AiJobRequest
import iter.ai.dto.AiJobStatus
import org.springframework.stereotype.Service
import java.util.LinkedHashMap
import java.util.Optional
import java.util.UUID

@Service
class PreviousConditionAnalysisService(private val jobs: ConditionAnalysisJobRepository, private val client: AiServiceClient) {
    // 같은 대여에서 성공한 수령·반납 비교 결과가 있으면 신고 AI가 참고할 안전한 항목만 반환합니다.
    fun findSucceededResult(rentalId: Long): Optional<Map<String, Any>> {
        val savedJob = jobs.findById(rentalId)

        if (savedJob.isEmpty) {
            return Optional.empty()
        }

        return try {
            val jobId = UUID.fromString(savedJob.get().jobId)
            val response = client.getJob(jobId)
            val featureType = response.featureType

            // 다른 기능의 결과나 완료되지 않은 결과는 신고 판단의 참고 자료로 사용하지 않습니다.
            val conditionFeature = featureType == AiJobRequest.FeatureType.RETURN_CONDITION_V1 ||
                featureType == AiJobRequest.FeatureType.RETURN_CONDITION_V2

            if (!conditionFeature || response.status != AiJobStatus.Status.SUCCEEDED || response.result == null) {
                Optional.empty()
            } else {
                val safeResult = sanitize(response.result)
                if (safeResult.isEmpty()) {
                    Optional.empty()
                } else {
                    safeResult["sourceFeature"] = featureType.name
                    safeResult["sourceRentalId"] = rentalId.toString()

                    response.completedAt?.let { completedAt ->
                        safeResult["completedAt"] = completedAt.toString()
                    }

                    Optional.of(safeResult)
                }
            }
        } catch (exception: AiServiceException) {
            // 이전 AI 서비스 조회에 실패해도 관리자는 현재 신고 분석을 계속할 수 있어야 합니다.
            Optional.empty()
        } catch (exception: IllegalArgumentException) {
            Optional.empty()
        }
    }

    // 이전 결과 전체를 전달하지 않고 판단, 근거, 사진 품질처럼 현재 검토에 필요한 값만 복사합니다.
    private fun sanitize(rawResult: Map<String, Any?>): MutableMap<String, Any> {
        val safe = LinkedHashMap<String, Any>()

        putText(safe, "assessment", rawResult["assessment"], 64)
        putText(safe, "suggestedCondition", rawResult["suggestedCondition"], 64)
        putText(safe, "summary", rawResult["summary"], MAX_SUMMARY_LENGTH)

        val reliability = rawResult["reliability"]

        if (reliability is Number) {
            val value = reliability.toDouble()

            if (value in 0.0..1.0) {
                safe["reliability"] = value
            }
        }

        val findings = sanitizeFindings(rawResult["findings"])

        if (findings.isNotEmpty()) {
            safe["findings"] = findings
        }

        val quality = sanitizeQuality(rawResult["quality"])

        if (quality.isNotEmpty()) {
            safe["quality"] = quality
        }

        val viewResults = sanitizeViewResults(rawResult["viewResults"])

        if (viewResults.isNotEmpty()) {
            safe["viewResults"] = viewResults
        }

        return safe
    }

    private fun sanitizeViewResults(rawViewResults: Any?): List<Map<String, Any>> {
        if (rawViewResults !is List<*>) {
            return emptyList()
        }

        val result = ArrayList<Map<String, Any>>()

        for (value in rawViewResults) {
            if (value !is Map<*, *> || result.size >= MAX_ITEMS) {
                continue
            }

            val safe = LinkedHashMap<String, Any>()

            putText(safe, "captureView", value["captureView"], 16)
            putText(safe, "assessment", value["assessment"], 64)
            putText(safe, "summary", value["summary"], 500)

            val findings = sanitizeFindings(value["findings"])

            if (findings.isNotEmpty()) {
                safe["findings"] = findings
            }

            val quality = sanitizeQuality(value["quality"])

            if (quality.isNotEmpty()) {
                safe["quality"] = quality
            }

            if (safe.isNotEmpty()) {
                result.add(safe)
            }
        }

        return java.util.List.copyOf(result)
    }

    private fun sanitizeFindings(rawFindings: Any?): List<Map<String, Any>> {
        val safeFindings = ArrayList<Map<String, Any>>()

        if (rawFindings !is List<*>) {
            return safeFindings
        }

        for (rawFinding in rawFindings) {
            if (rawFinding !is Map<*, *> || safeFindings.size >= MAX_ITEMS) {
                continue
            }

            val safeFinding = LinkedHashMap<String, Any>()

            putText(safeFinding, "type", rawFinding["type"], 64)
            putText(safeFinding, "severity", rawFinding["severity"], 64)
            putText(safeFinding, "description", rawFinding["description"], MAX_FINDING_DESCRIPTION_LENGTH)
            putText(safeFinding, "captureSlot", rawFinding["captureSlot"], 64)
            putText(safeFinding, "beforeImageId", rawFinding["beforeImageId"], 128)
            putText(safeFinding, "afterImageId", rawFinding["afterImageId"], 128)

            if (safeFinding.isNotEmpty()) {
                safeFindings.add(safeFinding)
            }
        }

        return safeFindings
    }

    private fun sanitizeQuality(rawQuality: Any?): Map<String, Any> {
        val safeQuality = LinkedHashMap<String, Any>()

        if (rawQuality !is Map<*, *>) {
            return safeQuality
        }

        putText(safeQuality, "status", rawQuality["status"], 64)

        val rawIssues = rawQuality["issues"]

        if (rawIssues is List<*>) {
            val safeIssues = rawIssues.asSequence()
                .filterIsInstance<String>()
                .map { value -> truncate(value, 200) }
                .filter(String::isNotBlank)
                .take(MAX_ISSUES)
                .toList()
            if (safeIssues.isNotEmpty()) {
                safeQuality["issues"] = java.util.List.copyOf(safeIssues)
            }
        }

        return safeQuality
    }

    private fun putText(target: MutableMap<String, Any>, key: String, value: Any?, maxLength: Int) {
        if (value is String && value.isNotBlank()) {
            target[key] = truncate(value, maxLength)
        }
    }

    private fun truncate(value: String, maxLength: Int): String {
        val trimmed = value.trim { character -> character.code <= 0x20 }

        return if (trimmed.length <= maxLength) {
            trimmed
        } else {
            trimmed.substring(0, maxLength)
        }
    }

    private companion object {
        private const val MAX_SUMMARY_LENGTH = 1_000
        private const val MAX_FINDING_DESCRIPTION_LENGTH = 500
        private const val MAX_ITEMS = 3
        private const val MAX_ISSUES = 5
    }
}
