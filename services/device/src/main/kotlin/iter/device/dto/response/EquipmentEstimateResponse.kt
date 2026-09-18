package iter.device.dto.response

import java.math.BigDecimal
import java.time.LocalDate

data class EquipmentEstimateResponse(
    val equipmentId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rentalDays: Int,
    val dailyPrice: BigDecimal,
    val totalPrice: BigDecimal,
)
