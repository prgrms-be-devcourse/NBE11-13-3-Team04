package iter.ai.dto

import jakarta.validation.constraints.AssertTrue
import kotlin.jvm.JvmRecord

// 사진은 저장된 정면·측면·후면 세 쌍으로 고정하고 외부 AI 전달 동의만 받습니다.
@JvmRecord
data class ConditionAnalysisRequest(@field:AssertTrue(message = "사진의 외부 AI 전달 동의가 필요합니다.") val externalAiConsent: Boolean)
