package com.example.iter.device.dto.response

import java.time.LocalDate

data class EquipmentScheduleResponse(
    val equipmentId: Long,
    val from: LocalDate?,
    val to: LocalDate?,
    val rentals: List<RentalScheduleItemResponse>,
)
