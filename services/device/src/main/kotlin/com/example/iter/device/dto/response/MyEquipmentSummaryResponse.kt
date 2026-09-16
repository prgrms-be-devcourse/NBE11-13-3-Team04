package com.example.iter.device.dto.response

import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.entity.ProductConditionType

import java.math.BigDecimal
import java.time.LocalDate

data class MyEquipmentSummaryResponse(
    val id: Long,
    val name: String,
    val category: EquipmentCategory,
    val dailyPrice: BigDecimal,
    val status: EquipmentStatus,
    val productCondition: ProductConditionType,
    val thumbnailUrl: String?,
    val availableFrom: LocalDate?,
    val availableTo: LocalDate?,
)
