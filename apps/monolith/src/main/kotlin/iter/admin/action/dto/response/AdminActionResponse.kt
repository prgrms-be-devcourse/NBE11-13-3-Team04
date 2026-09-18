package iter.admin.action.dto.response

import iter.common.audit.domain.entity.AdminActionTargetType
import iter.common.audit.domain.entity.AdminActionType
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminActionResponse(
    val actionId: Long?,
    val adminId: Long,
    val targetType: AdminActionTargetType,
    val targetId: Long,
    val action: AdminActionType,
    val reason: String?,
    val createdAt: LocalDateTime?
)
