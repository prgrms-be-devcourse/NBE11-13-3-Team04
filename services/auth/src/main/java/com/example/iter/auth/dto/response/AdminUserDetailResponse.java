package com.example.iter.auth.dto.response;

import com.example.iter.common.security.Role;
import com.example.iter.common.security.UserStatus;

import java.time.LocalDateTime;

public record AdminUserDetailResponse(
        Long userId,
        String email,
        String name,
        String nickName,
        String phone,
        Role role,
        UserStatus status,
        long rentedCount,
        long lentCount,
        long overdueCount,
        long reportCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
