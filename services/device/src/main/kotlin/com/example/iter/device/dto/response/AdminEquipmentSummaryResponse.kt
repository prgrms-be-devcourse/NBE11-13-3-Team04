package com.example.iter.device.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.EquipmentStatus

import java.math.BigDecimal
import java.time.LocalDateTime

data class AdminEquipmentSummaryResponse(
    val equipmentId: Long,
    val name: String,
    val category: EquipmentCategory,
    val dailyPrice: BigDecimal,
    val status: EquipmentStatus,
    val owner: UserSummary,
    val thumbnailUrl: String?,
    val createdAt: LocalDateTime?,
)
