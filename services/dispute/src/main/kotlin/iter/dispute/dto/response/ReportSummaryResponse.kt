package iter.dispute.dto.response

import iter.auth.api.UserSummary
import iter.dispute.domain.entity.ReportStatus
import iter.dispute.domain.entity.ReportTargetType
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReportSummaryResponse(
    val reportId: Long?,
    val reporter: UserSummary,
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: String,
    val status: ReportStatus,
    val createdAt: LocalDateTime?
)
