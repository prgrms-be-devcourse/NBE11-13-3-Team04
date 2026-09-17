package com.example.iter.auth.dto.request

import com.example.iter.common.security.UserStatus
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminUserStatusRequest(
    @field:NotNull(message = "변경할 회원 상태는 필수입니다.")
    val status: UserStatus?,

    @field:NotBlank(message = "관리자 처리 사유는 필수입니다.")
    @field:Size(max = 500, message = "관리자 처리 사유는 500자 이하여야 합니다.")
    val reason: String?
) {
    @JsonIgnore
    @AssertTrue(message = "회원 상태는 ACTIVE 또는 SUSPENDED로만 변경할 수 있습니다.")
    fun isAllowedStatus(): Boolean = status == null ||
        status == UserStatus.ACTIVE ||
        status == UserStatus.SUSPENDED
}
