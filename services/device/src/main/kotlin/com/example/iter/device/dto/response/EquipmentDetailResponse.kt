package com.example.iter.device.dto.response

import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.entity.ProductConditionType

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class EquipmentDetailResponse(
    val id: Long,
    val name: String,
    val category: EquipmentCategory,
    val description: String?,
    val dailyPrice: BigDecimal,
    val availableFrom: LocalDate?,
    val availableTo: LocalDate?,
    val status: EquipmentStatus,
    val productCondition: ProductConditionType,
    val conditionDetail: String?,
    val images: List<EquipmentImageResponse>,
    val owner: EquipmentOwnerResponse,
    val averageRating: Double,
    val reviewCount: Long,
    val createdAt: LocalDateTime?,
)
