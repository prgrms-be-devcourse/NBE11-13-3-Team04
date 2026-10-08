package iter.notification.domain.entity

import iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// receiverId/rentalId는 각각 auth/reservation 도메인 PK를 값으로만 참조 (도메인 간 결합 최소화 컨벤션).
// title/message(완성된 문장)를 저장하지 않는다 — params(보간 값)만 저장하고, 화면 표시 문구는
// 프론트가 type + params로 자체 i18n 사전을 통해 조립한다 (docs/i18n-frontend-handoff.md 참고).
// 이메일 발송용 문구는 NotificationService.create() 호출 시점에 NotificationMessages로 만들어서
// 그 자리에서만 쓰고 저장하지 않는다.
@Entity
@Table(
    name = "notification",
    indexes = [
        Index(name = "idx_notification_receiver_created_id", columnList = "receiver_id, created_at DESC, id DESC"),
        Index(name = "idx_notification_receiver_read_created_id", columnList = "receiver_id, is_read, created_at DESC, id DESC"),
    ],
)
class Notification @JvmOverloads constructor(
    @Column(name = "receiver_id", nullable = false)
    val receiverId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(name = "rental_id", nullable = false)
    val rentalId: Long,

    @Convert(converter = NotificationParamsConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    val params: Map<String, Any> = emptyMap(),

    read: Boolean = false,

    readAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseCreatedAtEntity() {

    @Column(name = "is_read", nullable = false)
    var read: Boolean = read
        protected set

    @Column(name = "read_at")
    var readAt: LocalDateTime? = readAt
        protected set

    fun isReceiver(userId: Long): Boolean = receiverId == userId

    fun markRead() {
        if (read) {
            return
        }
        read = true
        readAt = LocalDateTime.now()
    }
}
