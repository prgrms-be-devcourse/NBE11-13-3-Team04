package iter.device.dto.request

import iter.device.domain.entity.EquipmentStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class MyEquipmentSearchRequest(
    val status: EquipmentStatus?,
    val sort: EquipmentSort = EquipmentSort.LATEST,

    @field:Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    val page: Int = 0,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = 20,
)
