package iter.ai.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AiDtoValidationTest {

    @Test
    fun 상태_분석은_외부_AI_전달에_동의해야_한다() {
        val request = ConditionAnalysisRequest(false)

        assertThat(validator.validate(request))
            .extracting<String> { it.propertyPath.toString() }
            .containsExactly("externalAiConsent")
    }

    @Test
    fun 장비_초안은_이미지_키가_필수다() {
        val request = EquipmentDraftRequest(null, null, null)

        assertThat(validator.validate(request))
            .extracting<String> { it.propertyPath.toString() }
            .contains("imageKeys")
    }

    @Test
    fun 장비_초안의_각_이미지_키는_공백일_수_없다() {
        val request = EquipmentDraftRequest(listOf(" "), null, null)

        assertThat(validator.validate(request))
            .extracting<String> { it.propertyPath.toString() }
            .anyMatch { path -> path.startsWith("imageKeys") }
    }

    @Test
    fun 신고_분석_설명은_필수다() {
        val request = ReportAnalysisRequest(" ", true)

        assertThat(validator.validate(request))
            .extracting<String> { it.propertyPath.toString() }
            .contains("description")
    }

    @Test
    fun 신고_분석은_외부_AI_전달에_동의해야_한다() {
        val request = ReportAnalysisRequest("분석할 내용", false)

        assertThat(validator.validate(request))
            .extracting<String> { it.propertyPath.toString() }
            .containsExactly("externalAiConsent")
    }

    @Test
    fun AI_작업의_완료_상태를_구분한다() {
        val succeeded = jobStatus(AiJobStatus.Status.SUCCEEDED)
        val processing = jobStatus(AiJobStatus.Status.PROCESSING)

        assertThat(succeeded.isFinished()).isTrue()
        assertThat(processing.isFinished()).isFalse()
    }

    private fun jobStatus(status: AiJobStatus.Status) = AiJobStatus(
        jobId = null,
        featureType = null,
        status = status,
        result = null,
        errorMessage = null,
        provider = null,
        model = null,
        inputTokens = null,
        outputTokens = null,
        estimatedCostMicros = null,
        createdAt = null,
        completedAt = null
    )

    companion object {
        private lateinit var validatorFactory: ValidatorFactory
        private lateinit var validator: Validator

        @JvmStatic
        @BeforeAll
        fun setUpValidator() {
            validatorFactory = Validation.buildDefaultValidatorFactory()
            validator = validatorFactory.validator
        }

        @JvmStatic
        @AfterAll
        fun closeValidatorFactory() {
            validatorFactory.close()
        }
    }
}
