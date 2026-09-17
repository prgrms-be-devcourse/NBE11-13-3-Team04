package com.example.iter.ai.domain.repository

import com.example.iter.ai.domain.entity.ReportAnalysisJob
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ReportAnalysisJobRepository : JpaRepository<ReportAnalysisJob, Long> {
    fun countByAdminIdAndCreatedAtGreaterThanEqual(adminId: Long, start: LocalDateTime): Long
}
