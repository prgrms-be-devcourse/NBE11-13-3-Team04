package com.example.iter.auth.api;

import com.example.iter.common.security.UserStatus;

import java.util.Objects;

// 락을 잡은 시점의 회원 상태 스냅샷.
//
// status 에 requireNonNull 을 거는 이유: 이 값이 null 이면 SUSPENDED/DELETED 차단이
// 전부 false 로 떨어져 정지·탈퇴 회원이 조용히 통과한다. AuthUser 와 같은 종류의 함정이다.
public record UserLockView(
        Long userId,
        UserStatus status
) {
    public UserLockView {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
