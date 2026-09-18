package iter.device.dto.response

data class EquipmentListResponse(
    val content: List<EquipmentSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)
