package iter.admin.action.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminActionReasonRequest(
    @field:NotBlank(message = "관리자 처리 사유는 필수입니다.")
    @field:Size(max = 500, message = "관리자 처리 사유는 500자 이하여야 합니다.")
    val reason: String?
)
