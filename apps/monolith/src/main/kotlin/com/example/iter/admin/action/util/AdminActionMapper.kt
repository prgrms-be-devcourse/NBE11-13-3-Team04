package com.example.iter.admin.action.util

import com.example.iter.admin.action.dto.response.AdminActionResponse
import com.example.iter.common.audit.domain.entity.AdminAction
import org.springframework.stereotype.Component

@Component
class AdminActionMapper {
    fun toResponse(adminAction: AdminAction): AdminActionResponse = AdminActionResponse(
        adminAction.id,
        adminAction.adminId,
        adminAction.targetType,
        adminAction.targetId,
        adminAction.action,
        adminAction.reason,
        adminAction.createdAt
    )
}
