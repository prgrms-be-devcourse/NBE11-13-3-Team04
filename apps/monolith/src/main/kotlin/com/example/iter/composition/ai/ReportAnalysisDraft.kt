package com.example.iter.composition.ai

import com.example.iter.ai.service.ReportAnalysisContext
import java.util.LinkedHashMap

// DB 조회 단계에서 수집한 정보를 담아 두었다가 외부 이미지 검증 후 최종 AI 컨텍스트로 완성합니다.
class ReportAnalysisDraft {
    // 검증된 상태·횟수·금액처럼 AI가 임의로 해석하면 안 되는 서버 사실입니다.
    val systemFacts: MutableMap<String, String> = LinkedHashMap()

    // 사용자 작성 문구 중 개인정보 제거를 거쳐 AI에 전달할 수 있는 내용입니다.
    val publicContent: MutableMap<String, String> = LinkedHashMap()

    // 트랜잭션 종료 후 S3 메타데이터를 확인할 이미지 후보입니다.
    val imageCandidates: MutableList<ReportImageCandidate> = ArrayList()

    // 같은 거래의 반납 비교 결과가 있으면 사진 중복 전송 대신 재사용합니다.
    val priorConditionAnalysis: MutableMap<String, Any> = LinkedHashMap()

    fun addFact(key: String, value: Any?) {
        if (value != null) {
            systemFacts[key] = value.toString()
        }
    }

    // 여러 경로에서 같은 증빙을 발견해도 논리 이미지 ID별로 한 번만 전달합니다.
    fun addImageCandidate(path: String?, imageId: String, captureSlot: String) {
        if (imageCandidates.none { candidate -> candidate.imageId == imageId }) {
            imageCandidates += ReportImageCandidate(path, imageId, captureSlot)
        }
    }

    fun toContext(evidenceImages: List<Map<String, Any?>>): ReportAnalysisContext = ReportAnalysisContext(
        systemFacts,
        publicContent,
        evidenceImages,
        priorConditionAnalysis
    )
}

data class ReportImageCandidate(
    val path: String?,
    val imageId: String,
    val captureSlot: String
)
