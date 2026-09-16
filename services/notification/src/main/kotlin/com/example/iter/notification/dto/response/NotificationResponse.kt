package com.example.iter.notification.dto.response

import com.example.iter.notification.domain.entity.Notification
import com.example.iter.notification.domain.entity.NotificationType
import java.time.LocalDateTime

// title/message(완성된 문장)가 아니라 type + params를 내려준다 — 프론트가 이 둘로
// 자체 i18n 사전에서 표시 문구(제목/본문)를 조립한다 (docs/i18n-frontend-handoff.md 참고).
data class NotificationResponse(
    val id: Long?,
    val type: NotificationType,
    val params: Map<String, Any>,
    val rentalId: Long,
    val read: Boolean,
    // 원래 자바 record 컴포넌트라 null을 허용했다 — 실제로는 저장(persist) 시점에
    // @CreatedDate가 채워주지만, 아직 저장되지 않은 인스턴스(예: save()를 모킹하는
    // 테스트)에서도 이 DTO를 만들 수 있어 그 자리에서 터지지 않게 nullable 그대로 둔다.
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun from(notification: Notification): NotificationResponse = NotificationResponse(
            notification.id,
            notification.type,
            notification.params,
            notification.rentalId,
            notification.read,
            notification.createdAt,
        )
    }
}
