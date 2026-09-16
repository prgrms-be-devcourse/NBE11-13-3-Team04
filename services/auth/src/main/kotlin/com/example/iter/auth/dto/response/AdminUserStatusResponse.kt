package com.example.iter.auth.dto.response

import com.example.iter.auth.domain.entity.User
import com.example.iter.common.security.UserStatus
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminUserStatusResponse(val userId: Long?, val status: UserStatus?, val updatedAt: LocalDateTime?) {
    companion object {
        @JvmStatic
        fun from(user: User): AdminUserStatusResponse = AdminUserStatusResponse(
            user.id,
            user.status,
            user.updatedAt
        )
    }
}
