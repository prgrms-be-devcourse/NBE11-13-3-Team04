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
        val rental = rentalQueryPort.find(event.rentalId()).orElse(null) ?: return
        val ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null) ?: return
        val renter = findUser(rental.renterId())
        val owner = findUser(ownerId)

        val productName = rental.productName()

        if (owner != null) {
            val paymentCompletedOwner = NotificationMessages.paymentCompletedOwner(
                renterNameOf(renter), productName, owner.preferredLanguage(),
            )
            notify {
                notificationService.create(
                    owner.userId(), owner.email(), NotificationType.PAYMENT_COMPLETED_OWNER,
                    paymentCompletedOwner.title, paymentCompletedOwner.message, paymentCompletedOwner.params,
                    rental.rentalId(),
                )
            }
            val rentalRequested = NotificationMessages.rentalRequested(productName, owner.preferredLanguage())
            notify {
                notificationService.create(
                    owner.userId(), owner.email(), NotificationType.RENTAL_REQUESTED,
                    rentalRequested.title, rentalRequested.message, rentalRequested.params,
                    rental.rentalId(),
                )
            }
        }
        if (renter != null) {
            val paymentCompletedRenter = NotificationMessages.paymentCompletedRenter(productName, renter.preferredLanguage())
            notify {
                notificationService.create(
                    renter.userId(), renter.email(), NotificationType.PAYMENT_COMPLETED_RENTER,
                    paymentCompletedRenter.title, paymentCompletedRenter.message, paymentCompletedRenter.params,
                    rental.rentalId(),
                )
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalApproved(event: RentalApprovedEvent) {
        val rental = rentalQueryPort.find(event.rentalId()).orElse(null) ?: return
        val renter = findUser(rental.renterId()) ?: return
        val content = NotificationMessages.rentalApproved(rental.productName(), renter.preferredLanguage())
        notify {
            notificationService.create(
                renter.userId(), renter.email(), NotificationType.RENTAL_APPROVED,
                content.title, content.message, content.params,
                rental.rentalId(),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalRejected(event: RentalRejectedEvent) {
        val rental = rentalQueryPort.find(event.rentalId()).orElse(null) ?: return
        val renter = findUser(rental.renterId()) ?: return

        val refunded = paymentQueryPort.isRefundedForRental(rental.rentalId())

        val content = NotificationMessages.rentalRejected(
            rental.productName(), rental.rejectReason(), refunded, renter.preferredLanguage(),
        )
        notify {
            notificationService.create(
                renter.userId(), renter.email(), NotificationType.RENTAL_REJECTED,
                content.title, content.message, content.params,
                rental.rentalId(),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalCanceled(event: RentalCanceledEvent) {
        val rental = rentalQueryPort.find(event.rentalId()).orElse(null) ?: return
        val ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null) ?: return
        val owner = findUser(ownerId) ?: return
        val renter = findUser(rental.renterId())

        val content = NotificationMessages.rentalCanceled(renterNameOf(renter), rental.productName(), owner.preferredLanguage())
        notify {
            notificationService.create(
                owner.userId(), owner.email(), NotificationType.RENTAL_CANCELED,
                content.title, content.message, content.params,
                rental.rentalId(),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalReceived(event: RentalReceivedEvent) {
        val rental = rentalQueryPort.find(event.rentalId()).orElse(null) ?: return
        val ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null) ?: return
        val owner = findUser(ownerId) ?: return
        val renter = findUser(rental.renterId())

        val content = NotificationMessages.rentalReceived(renterNameOf(renter), rental.productName(), owner.preferredLanguage())
        notify {
            notificationService.create(
                owner.userId(), owner.email(), NotificationType.RENTAL_RECEIVED,
                content.title, content.message, content.params,
                rental.rentalId(),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRentalReviewCreated(event: RentalReviewCreatedEvent) {
        val review = rentalReviewQueryPort.find(event.reviewId()).orElse(null) ?: return
        val rental: RentalInfo = rentalQueryPort.find(review.rentalId()).orElse(null) ?: return
        val reviewer = findUser(review.reviewerId())
        val reviewee = findUser(review.revieweeId()) ?: return

        val content = NotificationMessages.reviewReceived(
            reviewer?.name() ?: FALLBACK_REVIEWER_NAME,
            rental.productName(),
            review.rating(),
            reviewee.preferredLanguage(),
        )
        notify {
            notificationService.create(
                reviewee.userId(), reviewee.email(), NotificationType.REVIEW_RECEIVED,
                content.title, content.message, content.params,
                rental.rentalId(),
            )
        }
    }

    private fun findUser(userId: Long): UserProfile? = userQueryPort.findProfile(userId).orElse(null)

    private fun renterNameOf(renter: UserProfile?): String = renter?.name() ?: FALLBACK_RENTER_NAME

    private fun notify(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            log.error("알림 생성 실패", e)
        }
    }

    private companion object {
        private const val FALLBACK_RENTER_NAME = "대여자"
        private const val FALLBACK_REVIEWER_NAME = "상대방"
        private val log = LoggerFactory.getLogger(NotificationEventListener::class.java)
    }
}
