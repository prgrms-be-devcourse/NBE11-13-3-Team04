package iter.ai.dto

import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.JvmRecord

@JvmRecord
data class ReportAnalysisResponse(
    val jobId: UUID?,
    val status: String?,
    val analysis: Map<String, Any?>?,
    val message: String?,
    val requestedAt: LocalDateTime?
)
