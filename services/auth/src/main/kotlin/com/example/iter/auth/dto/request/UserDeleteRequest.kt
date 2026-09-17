package com.example.iter.auth.dto.request

import jakarta.validation.constraints.Size

@JvmRecord
data class UserDeleteRequest(
    @field:Size(min = 8, max = 32, message = "비밀번호는 8자 이상 32자 이하여야 합니다.")
    val password: String?,
)
