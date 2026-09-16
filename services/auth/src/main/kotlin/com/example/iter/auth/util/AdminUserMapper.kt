package com.example.iter.auth.util

import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.dto.response.AdminUserDetailResponse
import org.springframework.stereotype.Component

@Component
class AdminUserMapper {
    // 회원 정보와 거래·연체·신고 집계값을 관리자 회원 상세 응답으로 변환합니다.
    fun toDetail(
        user: User,
        rentedCount: Long,
        lentCount: Long,
        overdueCount: Long,
        reportCount: Long
    ): AdminUserDetailResponse = AdminUserDetailResponse(
        user.id,
        user.email,
        user.name,
        user.nickname,
        user.phone,
        user.role,
        user.status,
        rentedCount,
        lentCount,
        overdueCount,
        reportCount,
        user.createdAt,
        user.updatedAt
    )
}
