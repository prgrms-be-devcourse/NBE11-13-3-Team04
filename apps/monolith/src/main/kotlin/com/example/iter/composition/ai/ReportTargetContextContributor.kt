package com.example.iter.composition.ai

import com.example.iter.dispute.domain.entity.Report
import com.example.iter.dispute.domain.entity.ReportTargetType

// 신고 대상 유형별 컨텍스트 수집 전략입니다. 새 유형은 구현체 추가로 확장할 수 있습니다.
interface ReportTargetContextContributor {
    val targetType: ReportTargetType

    fun contribute(report: Report, reviewedDescription: String?, draft: ReportAnalysisDraft)
}
