package com.example.iter.common.audit.service

import com.example.iter.common.audit.domain.entity.AdminAction
import com.example.iter.common.audit.domain.entity.AdminActionTargetType
import com.example.iter.common.audit.domain.entity.AdminActionType
import com.example.iter.common.audit.domain.repository.AdminActionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminActionService(private val adminActionRepository: AdminActionRepository) {
    // 관리자 상태 변경 트랜잭션에 참여해 대상 변경과 감사 이력이 함께 커밋되거나 롤백되게 합니다.
    @Transactional
    fun record(adminId: Long, targetType: AdminActionTargetType, targetId: Long, action: AdminActionType, reason: String?) {
        adminActionRepository.save(
            AdminAction(
                adminId = adminId,
                targetType = targetType,
                targetId = targetId,
                action = action,
                reason = reason
            )
        )
    }
}
