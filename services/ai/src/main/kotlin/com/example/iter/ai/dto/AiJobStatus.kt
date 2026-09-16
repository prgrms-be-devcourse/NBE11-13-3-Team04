package com.example.iter.ai.dto

import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.JvmRecord

@JvmRecord
data class AiJobStatus(
    val jobId: UUID?,
    val featureType: AiJobRequest.FeatureType?,
    val status: Status?,
    val result: Map<String, Any?>?,
    val errorMessage: String?,
    val provider: String?,
    val model: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val estimatedCostMicros: Long?,
    val createdAt: LocalDateTime?,
    val completedAt: LocalDateTime?
) {
    enum class Status {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }

    fun isFinished(): Boolean = status == Status.SUCCEEDED || status == Status.FAILED
}
