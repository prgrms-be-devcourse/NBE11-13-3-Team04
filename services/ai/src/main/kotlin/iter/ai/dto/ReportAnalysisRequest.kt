package iter.ai.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReportAnalysisRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val description: String?,

    @field:AssertTrue(message = "개인정보 제거와 외부 AI 전달 확인이 필요합니다.")
    val externalAiConsent: Boolean
)
