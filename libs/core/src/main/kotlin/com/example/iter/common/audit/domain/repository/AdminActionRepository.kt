package com.example.iter.common.audit.domain.repository

import com.example.iter.common.audit.domain.entity.AdminAction
import com.example.iter.common.audit.domain.entity.AdminActionTargetType
import com.example.iter.common.audit.domain.entity.AdminActionType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface AdminActionRepository : JpaRepository<AdminAction, Long> {
    // 생성 시각과 ID를 복합 커서로 사용해 같은 시각의 이력이 중복되거나 누락되지 않게 합니다.
    @Query(
        """
        select a
        from AdminAction a
        where (:targetType is null or a.targetType = :targetType)
          and (:targetId is null or a.targetId = :targetId)
          and (:action is null or a.action = :action)
          and (
                :cursorCreatedAt is null
                or a.createdAt < :cursorCreatedAt
                or (a.createdAt = :cursorCreatedAt and a.id < :cursorId)
              )
        order by a.createdAt desc, a.id desc
        """
    )
    fun searchForAdminByCursor(
        @Param("targetType")
        targetType: AdminActionTargetType?,

        @Param("targetId")
        targetId: Long?,

        @Param("action")
        action: AdminActionType?,

        @Param("cursorCreatedAt")
        cursorCreatedAt: LocalDateTime?,

        @Param("cursorId")
        cursorId: Long?,

        pageable: Pageable
    ): List<AdminAction>
}
