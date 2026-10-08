package iter.ai.dto

import java.util.UUID
import kotlin.jvm.JvmRecord

@JvmRecord
data class AiJobAccepted(val jobId: UUID?, val status: AiJobStatus.Status?, val duplicate: Boolean)
