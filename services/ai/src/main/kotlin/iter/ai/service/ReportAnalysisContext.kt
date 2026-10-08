package iter.ai.service

import java.util.LinkedHashMap

// 신고 유형에 맞게 Core에서 조회한 안전한 분석 자료와 관련 사진을 불변 snapshot으로 묶습니다.
class ReportAnalysisContext(
    systemFacts: Map<String, String>,
    publicContent: Map<String, String>,
    evidenceImages: List<Map<String, Any?>>,
    priorConditionAnalysis: Map<String, Any>
) {
    val systemFacts: Map<String, String> = LinkedHashMap(systemFacts)
    val publicContent: Map<String, String> = LinkedHashMap(publicContent)
    val evidenceImages: List<Map<String, Any?>> = java.util.List.copyOf(evidenceImages)
    val priorConditionAnalysis: Map<String, Any> = LinkedHashMap(priorConditionAnalysis)

    // 기존 Java record 접근자와의 호환성을 유지합니다.
    fun systemFacts(): Map<String, String> = systemFacts

    fun publicContent(): Map<String, String> = publicContent

    fun evidenceImages(): List<Map<String, Any?>> = evidenceImages

    fun priorConditionAnalysis(): Map<String, Any> = priorConditionAnalysis
}
