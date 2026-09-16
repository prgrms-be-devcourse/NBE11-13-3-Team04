package com.example.iter.device.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.entity.ProductConditionType

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class AdminEquipmentDetailResponse(
    val equipmentId: Long,
    val owner: UserSummary,
    val category: EquipmentCategory,
    val name: String,
    val description: String?,
    val dailyPrice: BigDecimal,
    val availableFrom: LocalDate?,
    val availableTo: LocalDate?,
    val status: EquipmentStatus,
    val productCondition: ProductConditionType,
    val conditionDetail: String?,
    val images: List<ImageResponse>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
