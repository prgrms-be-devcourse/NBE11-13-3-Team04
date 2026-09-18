package iter.ai.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// 거래별 최초 반납 비교 요청의 UUID와 입력 원문을 보존하는 Spring 측 작업 기록입니다.
@Entity
@Table(
    name = "condition_analysis_job",
    indexes = [
        Index(name = "idx_condition_ai_owner_created", columnList = "owner_id,created_at")
    ]
)
open class ConditionAnalysisJob protected constructor() {
    @field:Id
    open var rentalId: Long = 0L
        protected set

    @field:Column(nullable = false, unique = true, length = 36)
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
        rentalId: Long,
        jobId: String,
        ownerId: Long,
        requestJson: String,
        createdAt: LocalDateTime
    ) : this() {
        this.rentalId = rentalId
        this.jobId = jobId
        this.ownerId = ownerId
        this.requestJson = requestJson
        this.createdAt = createdAt
    }
}
