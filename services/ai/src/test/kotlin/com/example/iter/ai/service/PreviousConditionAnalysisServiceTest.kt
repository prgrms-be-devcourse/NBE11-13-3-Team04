package com.example.iter.ai.service

import com.example.iter.ai.client.AiServiceClient
import com.example.iter.ai.client.AiServiceException
import com.example.iter.ai.domain.entity.ConditionAnalysisJob
import com.example.iter.ai.domain.repository.ConditionAnalysisJobRepository
import com.example.iter.ai.dto.AiJobRequest
import com.example.iter.ai.dto.AiJobStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.LinkedHashMap
import java.util.Optional
import java.util.UUID

class PreviousConditionAnalysisServiceTest {
    private val jobs: ConditionAnalysisJobRepository = mock()
    private val client: AiServiceClient = mock()
    private val service = PreviousConditionAnalysisService(jobs, client)

    @Test
    fun 성공한_상태_비교에서_허용된_참고_정보만_가져온다() {
        val rentalId = 930023L
        val jobId = UUID.randomUUID()
        val savedJob = job(rentalId, jobId)
        val rawResult = LinkedHashMap<String, Any>().apply {
            put("assessment", "CHANGE_SUSPECTED")
            put("suggestedCondition", "DAMAGED")
            put("reliability", 0.7)
            put("summary", "반납 사진에서 긁힘 후보가 보입니다.")
            put(
                "findings",
                listOf(
                    mapOf(
                        "type" to "긁힘",
                        "severity" to "낮음",
                        "description" to "본체 오른쪽 측면 하단의 선형 자국",
                        "unexpected" to "전달하면 안 되는 값"
                    )
                )
            )
            put("unexpected", "전달하면 안 되는 값")
        }

        whenever(jobs.findById(rentalId)).thenReturn(Optional.of(savedJob))
        whenever(client.getJob(jobId)).thenReturn(
            AiJobStatus(
                jobId,
                AiJobRequest.FeatureType.RETURN_CONDITION_V1,
                AiJobStatus.Status.SUCCEEDED,
                rawResult,
                null,
                "openai",
                "test-model",
                100L,
                50L,
                10L,
                LocalDateTime.now(),
                LocalDateTime.now()
            )
        )

        val result = service.findSucceededResult(rentalId).orElseThrow()

        assertThat(result)
            .containsEntry("sourceFeature", "RETURN_CONDITION_V1")
            .containsEntry("sourceRentalId", "930023")
            .containsEntry("assessment", "CHANGE_SUSPECTED")
            .containsEntry("reliability", 0.7)
            .doesNotContainKey("unexpected")
        assertThat(result.toString()).doesNotContain("전달하면 안 되는 값")
    }

    @Test
    fun 이전_AI_조회가_실패해도_신고_분석을_막지_않는다() {
        val rentalId = 1L
        val jobId = UUID.randomUUID()

        whenever(jobs.findById(rentalId)).thenReturn(Optional.of(job(rentalId, jobId)))
        whenever(client.getJob(jobId)).thenThrow(AiServiceException(503))

        assertThat(service.findSucceededResult(rentalId)).isEmpty()
    }

    private fun job(rentalId: Long, jobId: UUID) = ConditionAnalysisJob(
        rentalId,
        jobId.toString(),
        1L,
        "{}",
        LocalDateTime.now()
    )
}
