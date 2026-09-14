package com.example.iter.notification.service;

import com.example.iter.auth.api.UserProfile;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.notification.domain.entity.NotificationType;
import com.example.iter.payment.api.PaymentQueryPort;
import com.example.iter.payment.event.PaymentConfirmedEvent;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalQueryPort;
import com.example.iter.reservation.api.RentalReviewInfo;
import com.example.iter.reservation.api.RentalReviewQueryPort;
import com.example.iter.reservation.event.RentalApprovedEvent;
import com.example.iter.reservation.event.RentalCanceledEvent;
import com.example.iter.reservation.event.RentalReceivedEvent;
import com.example.iter.reservation.event.RentalRejectedEvent;
import com.example.iter.reservation.event.RentalReviewCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Rental/Payment 트랜잭션이 "커밋된 이후"에만 반응한다 (phase = AFTER_COMMIT).
// 예를 들어 approveRental()이 재고 충돌로 롤백되면 알림도 나가면 안 되기 때문.
// 알림 생성 하나가 실패해도(예: DB 커넥션 순간 장애) 같은 이벤트로 보낼 나머지 알림까지 막히면 안 되므로
// 각 알림 생성을 개별적으로 try-catch해서 격리한다.
//
// 다른 도메인의 데이터는 전부 포트로만 읽는다. 이 리스너는 자체 트랜잭션이 없어서
// 포트 호출마다 별도 읽기 트랜잭션이 열린다 — 원 트랜잭션은 이미 커밋된 뒤다.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final String FALLBACK_RENTER_NAME = "대여자";
    private static final String FALLBACK_REVIEWER_NAME = "상대방";

    private final NotificationService notificationService;
    private final RentalQueryPort rentalQueryPort;
    private final RentalReviewQueryPort rentalReviewQueryPort;
    private final EquipmentQueryPort equipmentQueryPort;
    private final UserQueryPort userQueryPort;
    private final PaymentQueryPort paymentQueryPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        RentalInfo rental = rentalQueryPort.find(event.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        Long ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null);
        if (ownerId == null) {
            return;
        }
        UserProfile renter = findUser(rental.renterId());
        UserProfile owner = findUser(ownerId);

        String productName = rental.productName();

        if (owner != null) {
            NotificationMessages.Content paymentCompletedOwner = NotificationMessages.paymentCompletedOwner(
                    renterNameOf(renter), productName, owner.preferredLanguage());
            notify(() -> notificationService.create(
                    owner.userId(), owner.email(), NotificationType.PAYMENT_COMPLETED_OWNER,
                    paymentCompletedOwner.title(), paymentCompletedOwner.message(), paymentCompletedOwner.params(),
                    rental.rentalId()
            ));
            NotificationMessages.Content rentalRequested =
                    NotificationMessages.rentalRequested(productName, owner.preferredLanguage());
            notify(() -> notificationService.create(
                    owner.userId(), owner.email(), NotificationType.RENTAL_REQUESTED,
                    rentalRequested.title(), rentalRequested.message(), rentalRequested.params(),
                    rental.rentalId()
            ));
        }
        if (renter != null) {
            NotificationMessages.Content paymentCompletedRenter =
                    NotificationMessages.paymentCompletedRenter(productName, renter.preferredLanguage());
            notify(() -> notificationService.create(
                    renter.userId(), renter.email(), NotificationType.PAYMENT_COMPLETED_RENTER,
                    paymentCompletedRenter.title(), paymentCompletedRenter.message(), paymentCompletedRenter.params(),
                    rental.rentalId()
            ));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRentalApproved(RentalApprovedEvent event) {
        RentalInfo rental = rentalQueryPort.find(event.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        UserProfile renter = findUser(rental.renterId());
        if (renter == null) {
            return;
        }
        NotificationMessages.Content content = NotificationMessages.rentalApproved(
                rental.productName(), renter.preferredLanguage());
        notify(() -> notificationService.create(
                renter.userId(), renter.email(), NotificationType.RENTAL_APPROVED,
                content.title(), content.message(), content.params(),
                rental.rentalId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRentalRejected(RentalRejectedEvent event) {
        RentalInfo rental = rentalQueryPort.find(event.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        UserProfile renter = findUser(rental.renterId());
        if (renter == null) {
            return;
        }

        boolean refunded = paymentQueryPort.isRefundedForRental(rental.rentalId());

        NotificationMessages.Content content = NotificationMessages.rentalRejected(
                rental.productName(), rental.rejectReason(), refunded, renter.preferredLanguage());
        notify(() -> notificationService.create(
                renter.userId(), renter.email(), NotificationType.RENTAL_REJECTED,
                content.title(), content.message(), content.params(),
                rental.rentalId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRentalCanceled(RentalCanceledEvent event) {
        RentalInfo rental = rentalQueryPort.find(event.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        Long ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null);
        if (ownerId == null) {
            return;
        }
        UserProfile owner = findUser(ownerId);
        UserProfile renter = findUser(rental.renterId());
        if (owner == null) {
            return;
        }

        NotificationMessages.Content content = NotificationMessages.rentalCanceled(
                renterNameOf(renter), rental.productName(), owner.preferredLanguage());
        notify(() -> notificationService.create(
                owner.userId(), owner.email(), NotificationType.RENTAL_CANCELED,
                content.title(), content.message(), content.params(),
                rental.rentalId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRentalReceived(RentalReceivedEvent event) {
        RentalInfo rental = rentalQueryPort.find(event.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        Long ownerId = equipmentQueryPort.findOwnerId(rental.equipmentId()).orElse(null);
        if (ownerId == null) {
            return;
        }
        UserProfile owner = findUser(ownerId);
        UserProfile renter = findUser(rental.renterId());
        if (owner == null) {
            return;
        }

        NotificationMessages.Content content = NotificationMessages.rentalReceived(
                renterNameOf(renter), rental.productName(), owner.preferredLanguage());
        notify(() -> notificationService.create(
                owner.userId(), owner.email(), NotificationType.RENTAL_RECEIVED,
                content.title(), content.message(), content.params(),
                rental.rentalId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRentalReviewCreated(RentalReviewCreatedEvent event) {
        RentalReviewInfo review = rentalReviewQueryPort.find(event.reviewId()).orElse(null);
        if (review == null) {
            return;
        }
        RentalInfo rental = rentalQueryPort.find(review.rentalId()).orElse(null);
        if (rental == null) {
            return;
        }
        UserProfile reviewer = findUser(review.reviewerId());
        UserProfile reviewee = findUser(review.revieweeId());
        if (reviewee == null) {
            return;
        }

        NotificationMessages.Content content = NotificationMessages.reviewReceived(
                reviewer != null ? reviewer.name() : FALLBACK_REVIEWER_NAME,
                rental.productName(),
                review.rating(),
                reviewee.preferredLanguage());
        notify(() -> notificationService.create(
                reviewee.userId(), reviewee.email(), NotificationType.REVIEW_RECEIVED,
                content.title(), content.message(), content.params(),
                rental.rentalId()
        ));
    }

    private UserProfile findUser(Long userId) {
        return userQueryPort.findProfile(userId).orElse(null);
    }

    private static String renterNameOf(UserProfile renter) {
        return renter != null ? renter.name() : FALLBACK_RENTER_NAME;
    }

    private void notify(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("알림 생성 실패", e);
        }
    }
}
