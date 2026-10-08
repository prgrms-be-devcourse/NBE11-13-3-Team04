package iter.auth.dto.response

import iter.auth.domain.entity.User
import iter.common.security.Role
import iter.common.security.UserStatus
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminUserSummaryResponse(
    val userId: Long?,
    val email: String?,
    val name: String?,
    val nickName: String?,
    val role: Role?,
    val status: UserStatus?,
    val createdAt: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun from(user: User): AdminUserSummaryResponse = AdminUserSummaryResponse(
            user.id,
            user.email,
            user.name,
            user.nickname,
            user.role,
            user.status,
            user.createdAt
        )
    }
}
