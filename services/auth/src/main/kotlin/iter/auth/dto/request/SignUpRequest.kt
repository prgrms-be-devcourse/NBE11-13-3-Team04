package iter.auth.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@JvmRecord
data class SignUpRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 32, message = "비밀번호는 8자 이상 32자 이하여야 합니다.")
    val password: String,

    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(max = 20, message = "이름은 20자 이하여야 합니다.")
    val name: String,

    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
    val nickname: String,

    @field:NotBlank(message = "연락처는 필수입니다.")
    @field:Pattern(regexp = "^\$|^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    val phone: String,
)
