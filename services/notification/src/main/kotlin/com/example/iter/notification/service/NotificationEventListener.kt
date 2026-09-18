package com.example.iter.notification.service

import com.example.iter.auth.api.UserProfile
import com.example.iter.auth.api.UserQueryPort
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.notification.domain.entity.NotificationType
import com.example.iter.payment.api.PaymentQueryPort
import com.example.iter.payment.event.PaymentConfirmedEvent
import com.example.iter.reservation.api.RentalInfo
import com.example.iter.reservation.api.RentalQueryPort
import com.example.iter.reservation.api.RentalReviewQueryPort
import com.example.iter.reservation.event.RentalApprovedEvent
import com.example.iter.reservation.event.RentalCanceledEvent
import com.example.iter.reservation.event.RentalReceivedEvent
import com.example.iter.reservation.event.RentalRejectedEvent
import com.example.iter.reservation.event.RentalReviewCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// Rental/Payment 트랜잭션이 "커밋된 이후"에만 반응한다 (phase = AFTER_COMMIT).
// 예를 들어 approveRental()이 재고 충돌로 롤백되면 알림도 나가면 안 되기 때문.
// 알림 생성 하나가 실패해도(예: DB 커넥션 순간 장애) 같은 이벤트로 보낼 나머지 알림까지 막히면 안 되므로
// 각 알림 생성을 개별적으로 try-catch해서 격리한다.
//
// 다른 도메인의 데이터는 전부 포트로만 읽는다. 이 리스너는 자체 트랜잭션이 없어서
// 포트 호출마다 별도 읽기 트랜잭션이 열린다 — 원 트랜잭션은 이미 커밋된 뒤다.
@Component
class NotificationEventListener(
    private val notificationService: NotificationService,
    private val rentalQueryPort: RentalQueryPort,
    private val rentalReviewQueryPort: RentalReviewQueryPort,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val userQueryPort: UserQueryPort,
    private val paymentQueryPort: PaymentQueryPort,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentConfirmed(event: PaymentConfirmedEvent) {
        log.debug("알림 이벤트 수신 event=PaymentConfirmed rentalId={}", event.rentalId)
        val rental = findRental(event.rentalId) ?: return
        val ownerId = findOwnerId(rental.equipmentId) ?: return
        val renter = findUser(rental.renterId)
        val owner = findUser(ownerId)

        val productName = rental.productName

        if (owner != null) {
            notify(
                owner, NotificationType.PAYMENT_COMPLETED_OWNER,
                NotificationMessages.paymentCompletedOwner(renterNameOf(renter), productName, owner.preferredLanguage),
                rental.rentalId,
            )
            notify(
                owner, NotificationType.RENTAL_REQUESTED,
                NotificationMessages.rentalRequested(productName, owner.preferredLanguage),
                rental.rentalId,
            )
        } else {
            log.warn("알림 스킵 — 장비 등록자 조회 실패 rentalId={} ownerId={}", rental.rentalId, ownerId)
        }
        if (renter != null) {
            notify(
                renter, NotificationType.PAYMENT_COMPLETED_RENTER,
                NotificationMessages.paymentCompletedRenter(productName, renter.preferredLanguage),
                rental.rentalId,
            )
        } else {
            log.warn("알림 스킵 — 대여자 조회 실패 rentalId={} renterId={}", rental.rentalId, rental.renterId)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalApproved(event: RentalApprovedEvent) {
        log.debug("알림 이벤트 수신 event=RentalApproved rentalId={}", event.rentalId)
        val rental = findRental(event.rentalId) ?: return
        val renter = requireUser(rental.renterId) ?: return
        notify(
            renter, NotificationType.RENTAL_APPROVED,
            NotificationMessages.rentalApproved(rental.productName, renter.preferredLanguage),
            rental.rentalId,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalRejected(event: RentalRejectedEvent) {
        log.debug("알림 이벤트 수신 event=RentalRejected rentalId={}", event.rentalId)
        val rental = findRental(event.rentalId) ?: return
        val renter = requireUser(rental.renterId) ?: return

        val refunded = paymentQueryPort.isRefundedForRental(rental.rentalId)

        // rejectReason은 계약상 null 허용이다 — 사유 하나 때문에 거절 알림 자체가 날아가면 안 된다.
        val content = NotificationMessages.rentalRejected(
            rental.productName, rental.rejectReason ?: FALLBACK_REJECT_REASON, refunded, renter.preferredLanguage,
        )
        notify(renter, NotificationType.RENTAL_REJECTED, content, rental.rentalId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalCanceled(event: RentalCanceledEvent) {
        log.debug("알림 이벤트 수신 event=RentalCanceled rentalId={}", event.rentalId)
        val rental = findRental(event.rentalId) ?: return
        val ownerId = findOwnerId(rental.equipmentId) ?: return
        val owner = requireUser(ownerId) ?: return
        val renter = findUser(rental.renterId)

        val content = NotificationMessages.rentalCanceled(renterNameOf(renter), rental.productName, owner.preferredLanguage)
        notify(owner, NotificationType.RENTAL_CANCELED, content, rental.rentalId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalReceived(event: RentalReceivedEvent) {
        log.debug("알림 이벤트 수신 event=RentalReceived rentalId={}", event.rentalId)
        val rental = findRental(event.rentalId) ?: return
        val ownerId = findOwnerId(rental.equipmentId) ?: return
        val owner = requireUser(ownerId) ?: return
        val renter = findUser(rental.renterId)

        val content = NotificationMessages.rentalReceived(renterNameOf(renter), rental.productName, owner.preferredLanguage)
        notify(owner, NotificationType.RENTAL_RECEIVED, content, rental.rentalId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalReviewCreated(event: RentalReviewCreatedEvent) {
        log.debug("알림 이벤트 수신 event=RentalReviewCreated reviewId={}", event.reviewId)
        val review = rentalReviewQueryPort.find(event.reviewId).orElse(null)
        if (review == null) {
            log.warn("알림 스킵 — 리뷰 없음 reviewId={}", event.reviewId)
            return
        }
        val rental: RentalInfo = findRental(review.rentalId) ?: return
        val reviewer = findUser(review.reviewerId)
        val reviewee = requireUser(review.revieweeId) ?: return

        val content = NotificationMessages.reviewReceived(
            reviewer?.name ?: FALLBACK_REVIEWER_NAME,
            rental.productName,
            review.rating,
            reviewee.preferredLanguage,
        )
        notify(reviewee, NotificationType.REVIEW_RECEIVED, content, rental.rentalId)
    }

    // 아래 조회 헬퍼들은 결과가 비면 로그를 남긴다. 예전에는 elvis-return으로 조용히 빠져나가서
    // 알림이 통째로 안 나가도 흔적이 전혀 없었다 — 어느 조회가 비었는지 드러나야 원인을 좁힐 수 있다.
    private fun findRental(rentalId: Long): RentalInfo? {
        val rental = rentalQueryPort.find(rentalId).orElse(null)
        if (rental == null) {
            log.warn("알림 스킵 — 대여 정보 없음 rentalId={}", rentalId)
        }
        return rental
    }

    private fun findOwnerId(equipmentId: Long): Long? {
        val ownerId = equipmentQueryPort.findOwnerId(equipmentId).orElse(null)
        if (ownerId == null) {
            log.warn("알림 스킵 — 장비 등록자 없음 equipmentId={}", equipmentId)
        }
        return ownerId
    }

    private fun findUser(userId: Long): UserProfile? = userQueryPort.findProfile(userId).orElse(null)

    // 수신자가 반드시 있어야 하는 자리에서 쓴다(없으면 알림 자체가 성립하지 않는다).
    private fun requireUser(userId: Long): UserProfile? {
        val user = findUser(userId)
        if (user == null) {
            log.warn("알림 스킵 — 수신자 없음 userId={}", userId)
        }
        return user
    }

    private fun renterNameOf(renter: UserProfile?): String = renter?.name ?: FALLBACK_RENTER_NAME

    // 알림 하나가 실패해도(예: DB 커넥션 순간 장애) 같은 이벤트로 보낼 나머지 알림까지 막히면
    // 안 되므로 여기서 격리한다. email은 UserProfile 계약상 null이 허용되는데, 예전에는 !!로
    // 단정해서 null이 오면 KotlinNPE가 아래 catch에 먹히고 알림이 통째로 조용히 사라졌다.
    private fun notify(receiver: UserProfile, type: NotificationType, content: NotificationMessages.Content, rentalId: Long) {
        val email = receiver.email
        if (email == null) {
            log.warn("알림 스킵 — 수신자 이메일 없음 type={} userId={}", type, receiver.userId)
            return
        }
        try {
            notificationService.create(
                receiver.userId, email, type,
                content.title, content.message, content.params,
                rentalId,
            )
        } catch (e: Exception) {
            log.error("알림 생성 실패 type={} userId={}", type, receiver.userId, e)
        }
    }

    private companion object {
        private const val FALLBACK_RENTER_NAME = "대여자"
        private const val FALLBACK_REVIEWER_NAME = "상대방"
        private const val FALLBACK_REJECT_REASON = "사유가 등록되지 않았습니다."
        private val log = LoggerFactory.getLogger(NotificationEventListener::class.java)
    }
}
