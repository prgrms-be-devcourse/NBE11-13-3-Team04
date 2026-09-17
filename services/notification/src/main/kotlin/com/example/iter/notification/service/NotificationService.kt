package com.example.iter.notification.service

import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.mail.MailMessage
import com.example.iter.common.mail.MailService
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import com.example.iter.notification.config.NotificationProperties
import com.example.iter.notification.domain.entity.Notification
import com.example.iter.notification.domain.entity.NotificationType
import com.example.iter.notification.domain.repository.NotificationRepository
import com.example.iter.notification.dto.response.NotificationResponse
import com.example.iter.notification.sse.NotificationSseService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationSseService: NotificationSseService,
    private val mailService: MailService,
    private val notificationProperties: NotificationProperties,
) {

    // NotificationEventListener가 이벤트당 여러 번(owner/renter 각각) 호출한다.
    // 알림 저장은 성공했는데 SSE/메일만 실패하는 경우를 막기 위해, DB 저장 이후 실패 가능성이 있는 두 side effect는 각자 내부에서 예외를 삼키도록 구현
    // (NotificationSseService, JavaMailService).
    //
    // REQUIRES_NEW로 강제하는 이유: 이 메서드는 항상 @TransactionalEventListener(AFTER_COMMIT) 콜백에서 호출된다
    // — 원본 트랜잭션이 막 커밋된 직후라 기본 REQUIRED로는 새 트랜잭션이 제대로 시작되지 않고 조용히 아무 것도 커밋되지 않는 경우가 있다
    // (예외도 안 던져서 알아채기 어렵다)
    // REQUIRES_NEW로 독립된 트랜잭션을 확실히 새로 열어야 함
    //
    // title/message는 이메일 발송에만 쓰고 저장하지 않는다 — 저장/응답(SSE, API)엔 params만 들어간다.
    // 프론트가 type + params로 자체 i18n 사전을 통해 표시 문구를 조립한다.
    //
    // receiverId/rentalId를 Long? 로 받는 이유: NotificationEventListenerTest가 이 메서드를
    // verify(never()).create(any(), any(), any(), any(), any(), any(), any())로 검증하는데,
    // 타입 미지정 any()는 null을 반환하는 매처라 non-null Long(런타임 primitive)이면 언박싱
    // NPE가 난다(device D7/R6와 동일 함정) — 메서드 진입 직후 그림자 변수로 즉시 풀어서 나머지
    // 본문은 non-null로 그대로 쓴다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun create(
        receiverId: Long?,
        receiverEmail: String,
        type: NotificationType,
        title: String,
        message: String,
        params: Map<String, Any>,
        rentalId: Long?,
    ) {
        val receiverId = receiverId!!
        val rentalId = rentalId!!

        val notification = notificationRepository.save(
            Notification(
                receiverId = receiverId,
                type = type,
                rentalId = rentalId,
                params = params,
            ),
        )

        notificationSseService.send(receiverId, NotificationResponse.from(notification))

        if (type.requiresEmail) {
            // mailService.send()는 @Async라 실제 발송 실패는 그 메서드 안에서 잡히지만 스레드풀 큐가 가득 차서 작업 "제출" 자체가 거부되면 RejectedExecutionException이 이 호출부에서 동기적으로 튀어나온다
            // — 여기서도 못 잡으면 방금 저장한 Notification까지 롤백된다.
            try {
                mailService.send(MailMessage(receiverEmail, notificationProperties.mailFrom, title, message))
            } catch (e: Exception) {
                log.warn("메일 발송 요청 실패: receiverId={}", receiverId, e)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getNotifications(userId: Long, unreadOnly: Boolean, cursor: String?, size: Int): CursorPageResponse<NotificationResponse> {
        val cursorKey = CursorCodec.decode(cursor)
        val cursorCreatedAt = cursorKey?.createdAt
        val cursorId = cursorKey?.id

        // size + 1개를 가져와서, 실제로 나온 개수가 size보다 많으면 다음 페이지가 있다는 뜻이다
        // (count 쿼리 없이 hasNext를 판단하기 위한 트릭 — CursorPageResponse.from 참고).
        val limit: Pageable = PageRequest.of(0, size + 1)
        val notifications = if (unreadOnly) {
            notificationRepository.findNextUnreadByReceiverId(userId, cursorCreatedAt, cursorId, limit)
        } else {
            notificationRepository.findNextByReceiverId(userId, cursorCreatedAt, cursorId, limit)
        }

        return CursorPageResponse.from(
            notifications,
            size,
            NotificationResponse::from,
        ) { notification -> CursorKey(notification.createdAt!!, notification.id!!) }
    }

    @Transactional(readOnly = true)
    fun getUnreadCount(userId: Long): Long = notificationRepository.countByReceiverIdAndReadFalse(userId)

    @Transactional
    fun markRead(userId: Long, notificationId: Long): NotificationResponse {
        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { CustomException(ErrorCode.NOTIFICATION_NOT_FOUND) }

        if (!notification.isReceiver(userId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }

        notification.markRead()
        return NotificationResponse.from(notification)
    }

    @Transactional
    fun markAllRead(userId: Long): Int = notificationRepository.markAllAsRead(userId, LocalDateTime.now())

    private companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
    }
}
