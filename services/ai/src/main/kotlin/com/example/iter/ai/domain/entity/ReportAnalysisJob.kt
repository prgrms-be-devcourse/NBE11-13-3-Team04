package com.example.iter.ai.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// 신고 한 건당 AI 작업 하나만 보관합니다. 실제 분석 결과는 별도 AI DB에 저장됩니다.
// 신고별 최초 관리자 AI 검토 요청과 작업 UUID를 보존하는 Spring 측 작업 기록입니다.
@Entity
@Table(
    name = "report_analysis_job",
    indexes = [
        Index(name = "idx_report_ai_admin_created", columnList = "admin_id,created_at")
    ]
)
open class ReportAnalysisJob protected constructor() {
    @field:Id
    open var reportId: Long = 0L
        protected set

    @field:Column(nullable = false, unique = true, length = 36)
    open var jobId: String = ""
        protected set

    @field:Column(name = "admin_id", nullable = false)
    open var adminId: Long = 0L
        protected set

    @field:Column(nullable = false, columnDefinition = "TEXT")
    open var requestJson: String = ""
        protected set

    @field:Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.MIN
        protected set

    constructor(
        reportId: Long,
        jobId: String,
        adminId: Long,
        requestJson: String,
        createdAt: LocalDateTime
    ) : this() {
        this.reportId = reportId
        this.jobId = jobId
        this.adminId = adminId
        this.requestJson = requestJson
        this.createdAt = createdAt
    }
}
