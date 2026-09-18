package iter.dispute.dto.request

import iter.dispute.domain.entity.ReportStatus
import iter.dispute.domain.entity.ReportTargetType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

class AdminReportSearchRequest(
    val targetType: ReportTargetType?,

    val status: ReportStatus?,

    @field:Size(max = 200, message = "커서는 200자 이하여야 합니다.")
    val cursor: String?,

    size: Int?
) {
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = size ?: 20

    fun targetType(): ReportTargetType? = targetType
    fun status(): ReportStatus? = status
    fun cursor(): String? = cursor
    fun size(): Int = size
}
