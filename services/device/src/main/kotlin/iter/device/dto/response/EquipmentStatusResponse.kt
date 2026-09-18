package iter.device.dto.response

import iter.device.domain.entity.EquipmentStatus

import java.time.LocalDateTime

data class EquipmentStatusResponse(
    val equipmentId: Long,
    val status: EquipmentStatus,
    val updatedAt: LocalDateTime?,
)
