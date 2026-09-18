package iter.common.audit.domain.entity

import iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

// 다른 도메인의 식별자를 값으로만 기록해 감사 로그가 대상 도메인의 생명주기에 결합되지 않게 합니다.
@Entity
@Table(
    name = "admin_action",
    indexes = [
        Index(
            name = "idx_admin_action_created_id",
            columnList = "created_at DESC, id DESC"
        ),

        Index(
            name = "idx_admin_action_target_created_id",
            columnList = "target_type, target_id, action, created_at DESC, id DESC"
        )
    ]
)
open class AdminAction protected constructor() : BaseCreatedAtEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
        protected set

    @field:Column(name = "admin_id", nullable = false)
    open var adminId: Long = 0L
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "target_type", nullable = false, length = 20)
    open lateinit var targetType: AdminActionTargetType
        protected set

    @field:Column(name = "target_id", nullable = false)
    open var targetId: Long = 0L
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 30)
    open lateinit var action: AdminActionType
        protected set

    @field:Column(columnDefinition = "TEXT")
    open var reason: String? = null
        protected set

    constructor(
        adminId: Long,
        targetType: AdminActionTargetType,
        targetId: Long,
        action: AdminActionType,
        reason: String?
    ) : this() {
        this.adminId = adminId
        this.targetType = targetType
        this.targetId = targetId
        this.action = action
        this.reason = reason
    }
}
