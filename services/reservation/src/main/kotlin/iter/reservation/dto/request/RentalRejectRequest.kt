package iter.reservation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RentalRejectRequest(
    @field:NotBlank(message = "거절 사유는 필수입니다.")
    @field:Size(max = 200, message = "거절 사유는 200자 이하여야 합니다.")
    val reason: String?,
) {
    fun reason(): String? = reason
}
