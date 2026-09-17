package com.example.iter.device.dto.request

import com.example.iter.device.domain.entity.EquipmentStatus
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull

data class EquipmentStatusUpdateRequest(
    @field:NotNull(message = "변경할 장비 상태는 필수입니다.")
    val status: EquipmentStatus?,
) {
    @get:AssertTrue(message = "장비 상태는 ACTIVE 또는 INACTIVE만 요청할 수 있습니다.")
    val isOwnerManageableStatus: Boolean
        get() = status == null ||
            status == EquipmentStatus.ACTIVE ||
            status == EquipmentStatus.INACTIVE
}
