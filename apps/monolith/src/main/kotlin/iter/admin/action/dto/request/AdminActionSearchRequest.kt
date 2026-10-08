package iter.admin.action.dto.request

import iter.common.audit.domain.entity.AdminActionTargetType
import iter.common.audit.domain.entity.AdminActionType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

class AdminActionSearchRequest(
    val targetType: AdminActionTargetType?,

    @field:Positive(message = "대상 ID는 양수여야 합니다.")
    val targetId: Long?,

    val action: AdminActionType?,

    @field:Size(max = 200, message = "커서는 200자 이하여야 합니다.")
    val cursor: String?,

    size: Int?
) {
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = size ?: 20

    fun targetType(): AdminActionTargetType? = targetType
    fun targetId(): Long? = targetId
    fun action(): AdminActionType? = action
    fun cursor(): String? = cursor
    fun size(): Int = size
}
