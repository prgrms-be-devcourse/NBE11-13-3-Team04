package com.example.iter.device.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.entity.ProductConditionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class AdminEquipmentDetailResponse(
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
    images: List<ImageResponse>?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    val images: List<ImageResponse> = java.util.List.copyOf(images ?: emptyList())

    fun equipmentId(): Long = equipmentId
    fun owner(): UserSummary = owner
    fun category(): EquipmentCategory = category
    fun name(): String = name
    fun description(): String? = description
    fun dailyPrice(): BigDecimal = dailyPrice
    fun availableFrom(): LocalDate? = availableFrom
    fun availableTo(): LocalDate? = availableTo
    fun status(): EquipmentStatus = status
    fun productCondition(): ProductConditionType = productCondition
    fun conditionDetail(): String? = conditionDetail
    fun images(): List<ImageResponse> = images
    fun createdAt(): LocalDateTime? = createdAt
    fun updatedAt(): LocalDateTime? = updatedAt
}
