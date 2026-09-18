package iter.auth.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JvmRecord
data class PasswordChangeRequest(
    @field:NotBlank(message = "현재 비밀번호는 필수입니다.")
    val currentPassword: String,

    @field:NotBlank(message = "새 비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 32, message = "비밀번호는 8자 이상 32자 이하여야 합니다.")
    val newPassword: String,
)
