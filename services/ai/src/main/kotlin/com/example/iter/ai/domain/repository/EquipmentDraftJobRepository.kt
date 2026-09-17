package com.example.iter.ai.domain.repository

import com.example.iter.ai.domain.entity.EquipmentDraftJob
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.Optional

interface EquipmentDraftJobRepository : JpaRepository<EquipmentDraftJob, String> {
    fun findByJobIdAndOwnerId(jobId: String, ownerId: Long): Optional<EquipmentDraftJob>

    fun countByOwnerIdAndCreatedAtGreaterThanEqual(ownerId: Long, start: LocalDateTime): Long
}
