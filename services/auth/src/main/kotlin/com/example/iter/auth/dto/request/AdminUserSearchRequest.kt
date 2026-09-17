package com.example.iter.auth.dto.request

import com.example.iter.common.security.UserStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

class AdminUserSearchRequest(
    @field:Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
    val keyword: String?,

    val status: UserStatus?,

    @field:Size(max = 200, message = "커서는 200자 이하여야 합니다.")
    val cursor: String?,

    size: Int?
) {
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = size ?: 20

    fun keyword(): String? = keyword
    fun status(): UserStatus? = status
    fun cursor(): String? = cursor
    fun size(): Int = size
}
