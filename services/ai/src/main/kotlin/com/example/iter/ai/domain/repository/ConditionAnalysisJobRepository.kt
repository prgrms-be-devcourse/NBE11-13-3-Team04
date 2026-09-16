package com.example.iter.ai.domain.repository

import com.example.iter.ai.domain.entity.ConditionAnalysisJob
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ConditionAnalysisJobRepository : JpaRepository<ConditionAnalysisJob, Long> {
    fun countByOwnerIdAndCreatedAtGreaterThanEqual(ownerId: Long, start: LocalDateTime): Long
}
