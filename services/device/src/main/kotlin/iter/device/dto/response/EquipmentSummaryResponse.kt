package iter.device.dto.response

import iter.device.domain.entity.EquipmentCategory
import iter.device.domain.entity.ProductConditionType

import java.math.BigDecimal
import java.time.LocalDate

data class EquipmentSummaryResponse(
    val id: Long,
    val name: String,
    val category: EquipmentCategory,
    val dailyPrice: BigDecimal,
    val availableFrom: LocalDate?,
    val availableTo: LocalDate?,
    val productCondition: ProductConditionType,
    val thumbnailUrl: String?,
    val averageRating: Double,
    val reviewCount: Long,
)
