package com.example.iter.composition.ai

import com.example.iter.ai.service.ReportAnalysisContext
import org.springframework.stereotype.Service

@Service
class ReportAnalysisContextBuilder(
    private val snapshotLoader: ReportAnalysisSnapshotLoader,
    private val evidenceCollector: ReportEvidenceCollector
) {
    // DB 스냅샷을 먼저 완성한 뒤 트랜잭션 밖에서 S3 사진 메타데이터를 확인합니다.
    fun build(reportId: Long, reviewedDescription: String): ReportAnalysisContext {
        val draft = snapshotLoader.load(reportId, reviewedDescription)

        return evidenceCollector.resolve(draft)
    }
}
