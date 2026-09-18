package iter.ai.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AiJobRequestTest {
    @ParameterizedTest
    @CsvSource(
        "RETURN_CONDITION_V1,RENTAL",
        "REPORT_TRIAGE_V1,REPORT",
        "EQUIPMENT_DRAFT_V1,UPLOAD_BUNDLE"
    )
    fun 기능에_맞는_대상과_UUID를_생성한다(feature: AiJobRequest.FeatureType, sourceType: String) {
        val request = AiJobRequest.create(feature, "source-1", emptyMap())

        assertThat(request.jobId).isNotNull()
        assertThat(request.source?.type).isEqualTo(sourceType)
        assertThat(request.source?.id).isEqualTo("source-1")
        assertThat(request.inputHash).matches("sha256:[a-f0-9]{64}")
    }

    @Test
    fun 키_순서와_무관하게_같은_hash를_만든다() {
        val first = linkedMapOf<String, Any>(
            "name" to "카메라",
            "hints" to mapOf("b" to 2, "a" to 1)
        )
        val second = linkedMapOf<String, Any>(
            "hints" to mapOf("a" to 1, "b" to 2),
            "name" to "카메라"
        )
        val firstRequest = AiJobRequest.create(
            AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1,
            "1",
            first
        )
        val secondRequest = AiJobRequest.create(
            AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1,
            "1",
            second
        )

        assertThat(firstRequest.inputHash).isEqualTo(secondRequest.inputHash)
        assertThat(firstRequest.jobId).isNotEqualTo(secondRequest.jobId)
    }

    @Test
    fun 원본_Map이_바뀌어도_이미_만든_요청은_유지한다() {
        val hints = mutableMapOf<String, Any>("name" to "카메라")
        val request = AiJobRequest.create(
            AiJobRequest.FeatureType.EQUIPMENT_DRAFT_V1,
            "1",
            mapOf("hints" to hints)
        )
        hints["name"] = "변경한 이름"

        assertThat(request.payload).containsEntry("hints", mapOf("name" to "카메라"))
    }
}
