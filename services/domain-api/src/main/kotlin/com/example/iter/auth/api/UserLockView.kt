package com.example.iter.auth.api

import com.example.iter.common.security.UserStatus

// 락을 잡은 시점의 회원 상태 스냅샷.
//
// status 를 non-null 로 강제하는 이유: 이 값이 null 이면 SUSPENDED/DELETED 차단이
// 전부 false 로 떨어져 정지·탈퇴 회원이 조용히 통과한다. AuthUser 와 같은 종류의 함정이다.
@JvmRecord
data class UserLockView(
    val userId: Long,
    val status: UserStatus,
) {
    fun isActive(): Boolean = status == UserStatus.ACTIVE
}
