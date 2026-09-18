package iter.auth.dto.response

import iter.auth.domain.entity.User
import iter.common.security.UserStatus
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
