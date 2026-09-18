package iter.auth.dto.response

import iter.common.security.Role
import iter.common.security.UserStatus
import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminUserDetailResponse(
    val userId: Long?,
    val email: String?,
    val name: String?,
    val nickName: String?,
    val phone: String?,
    val role: Role?,
    val status: UserStatus?,
    val rentedCount: Long,
    val lentCount: Long,
    val overdueCount: Long,
    val reportCount: Long,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)
