package iter.device.dto.response

import java.time.LocalDate

data class EquipmentAvailabilityResponse(
    val equipmentId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val available: Boolean,
    val reason: AvailabilityReason?,
)
