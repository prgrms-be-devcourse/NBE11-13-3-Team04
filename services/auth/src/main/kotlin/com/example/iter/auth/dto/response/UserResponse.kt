package com.example.iter.auth.dto.response

import com.example.iter.auth.api.PreferredLanguage
import com.example.iter.auth.domain.entity.User
import com.example.iter.common.security.Role
import com.example.iter.common.security.UserStatus

@JvmRecord
data class UserResponse(
    val id: Long?,
    val email: String,
    val name: String,
    val nickname: String?,
    val phone: String?,
    val role: Role,
    val status: UserStatus,
    val preferredLanguage: PreferredLanguage,
) {
    companion object {
        @JvmStatic
        fun from(user: User): UserResponse =
            UserResponse(
                user.id,
                user.email,
                user.name,
                user.nickname,
                user.phone,
                user.role,
                user.status,
                user.preferredLanguage,
            )
    }
}
