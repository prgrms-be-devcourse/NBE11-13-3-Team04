package com.example.iter.ai.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// 사용자별 장비 초안 요청의 소유권과 재접수에 필요한 원본 JSON을 보존합니다.
@Entity
@Table(
    name = "equipment_draft_job",
    indexes = [
        Index(name = "idx_draft_owner_created", columnList = "owner_id,created_at")
    ]
)
open class EquipmentDraftJob protected constructor() {
    @field:Id
    @field:Column(length = 36)
    open var jobId: String = ""
        protected set

    @field:Column(name = "owner_id", nullable = false)
    open var ownerId: Long = 0L
        protected set

    @field:Column(nullable = false, columnDefinition = "TEXT")
    open var requestJson: String = ""
        protected set

    @field:Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime = LocalDateTime.MIN
        protected set

    constructor(
        jobId: String,
        ownerId: Long,
        requestJson: String,
        createdAt: LocalDateTime
    ) : this() {
        this.jobId = jobId
        this.ownerId = ownerId
        this.requestJson = requestJson
        this.createdAt = createdAt
    }
}
