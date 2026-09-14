package com.example.iter.notification.service;

import com.example.iter.auth.api.PreferredLanguage;
import com.example.iter.auth.api.UserProfile;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.notification.domain.entity.NotificationType;
import com.example.iter.payment.api.PaymentQueryPort;
import com.example.iter.payment.event.PaymentConfirmedEvent;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.api.RentalQueryPort;
import com.example.iter.reservation.api.RentalReviewQueryPort;
import com.example.iter.reservation.event.RentalApprovedEvent;
import com.example.iter.reservation.event.RentalCanceledEvent;
import com.example.iter.reservation.event.RentalReceivedEvent;
import com.example.iter.reservation.event.RentalRejectedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// AFTER_COMMIT 리스너 자체(트랜잭션 경계)는 스프링이 보장하는 영역이라 검증하지 않고,
// "이벤트가 들어오면 어떤 알림을 누구에게 만드는가"라는 이 리스너 고유의 로직만 검증한다.
//
// 타 도메인 데이터는 전부 포트로 주입되므로, 엔티티 픽스처(Rental/Equipment/User 빌더)가
// 값 객체로 바뀌었다. 대여 상태·장비 카테고리·가격처럼 알림과 무관한 필드는 더 이상 만들 필요가 없다.
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    private static final Long RENTAL_ID = 10L;
    private static final Long EQUIPMENT_ID = 1L;
    private static final Long RENTER_ID = 2L;
    private static final Long OWNER_ID = 99L;

    @Mock
    private NotificationService notificationService;
    @Mock
    private RentalQueryPort rentalQueryPort;
    @Mock
    private RentalReviewQueryPort rentalReviewQueryPort;
    @Mock
    private EquipmentQueryPort equipmentQueryPort;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private PaymentQueryPort paymentQueryPort;

    @InjectMocks
    private NotificationEventListener listener;

    private RentalInfo rental() {
        return new RentalInfo(RENTAL_ID, EQUIPMENT_ID, RENTER_ID, "소니 A7C2", "일정이 겹칩니다.",
                RentalStatus.REQUESTED, BigDecimal.valueOf(150000),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
    }

    private UserProfile user(Long id, String email, String name) {
        return new UserProfile(id, name, email, PreferredLanguage.KO);
    }

    @Test
    void 결제_확인_이벤트는_owner에게_2건_renter에게_1건_알림을_만든다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.of(OWNER_ID));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));
        when(userQueryPort.findProfile(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner@test.com", "등록자")));

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(OWNER_ID), eq("owner@test.com"), eq(NotificationType.PAYMENT_COMPLETED_OWNER),
                any(), any(), any(), eq(RENTAL_ID));
        verify(notificationService).create(
                eq(OWNER_ID), eq("owner@test.com"), eq(NotificationType.RENTAL_REQUESTED),
                any(), any(), any(), eq(RENTAL_ID));
        verify(notificationService).create(
                eq(RENTER_ID), eq("renter@test.com"), eq(NotificationType.PAYMENT_COMPLETED_RENTER),
                any(), any(), any(), eq(RENTAL_ID));
    }

    @Test
    void 대여_정보가_없으면_결제_확인_이벤트를_무시한다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.empty());

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(RENTAL_ID));

        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 알림_하나가_실패해도_같은_이벤트의_나머지_알림은_계속_생성된다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.of(OWNER_ID));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));
        when(userQueryPort.findProfile(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner@test.com", "등록자")));
        doThrow(new RuntimeException("일시적 DB 오류"))
                .when(notificationService)
                .create(eq(OWNER_ID), any(), eq(NotificationType.PAYMENT_COMPLETED_OWNER), any(), any(), any(), anyLong());

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(RENTAL_ID));

        verify(notificationService, times(3)).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 승인_이벤트는_renter에게_알림을_만든다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));

        listener.onRentalApproved(new RentalApprovedEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(RENTER_ID), eq("renter@test.com"), eq(NotificationType.RENTAL_APPROVED),
                any(), any(), any(), eq(RENTAL_ID));
    }

    @Test
    void 영어를_선호하는_수신자에게는_영어_이메일_문구를_만든다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        UserProfile englishRenter = new UserProfile(RENTER_ID, "대여자", "renter@test.com", PreferredLanguage.EN);
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(englishRenter));

        listener.onRentalApproved(new RentalApprovedEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(RENTER_ID), eq("renter@test.com"), eq(NotificationType.RENTAL_APPROVED),
                eq("Rental request approved"), startsWith("Your rental request for"), any(), eq(RENTAL_ID));
    }

    @Test
    void 환불된_거절이면_메시지에_환불_안내가_포함된다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));
        when(paymentQueryPort.isRefundedForRental(RENTAL_ID)).thenReturn(true);

        listener.onRentalRejected(new RentalRejectedEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(RENTER_ID), eq("renter@test.com"), eq(NotificationType.RENTAL_REJECTED),
                any(), contains("환불"), any(), eq(RENTAL_ID));
    }

    @Test
    void 환불이_없는_거절이면_메시지에_환불_안내가_없다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));
        when(paymentQueryPort.isRefundedForRental(RENTAL_ID)).thenReturn(false);

        listener.onRentalRejected(new RentalRejectedEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(RENTER_ID), eq("renter@test.com"), eq(NotificationType.RENTAL_REJECTED),
                any(), argThat((String message) -> !message.contains("환불")), any(), eq(RENTAL_ID));
    }

    @Test
    void 취소_이벤트는_owner에게_알림을_만든다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.of(OWNER_ID));
        when(userQueryPort.findProfile(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner@test.com", "등록자")));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));

        listener.onRentalCanceled(new RentalCanceledEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(OWNER_ID), eq("owner@test.com"), eq(NotificationType.RENTAL_CANCELED),
                any(), any(), any(), eq(RENTAL_ID));
    }

    @Test
    void 수령확인_이벤트는_owner에게_알림을_만든다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.of(OWNER_ID));
        when(userQueryPort.findProfile(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner@test.com", "등록자")));
        when(userQueryPort.findProfile(RENTER_ID)).thenReturn(Optional.of(user(RENTER_ID, "renter@test.com", "대여자")));

        listener.onRentalReceived(new RentalReceivedEvent(RENTAL_ID));

        verify(notificationService).create(
                eq(OWNER_ID), eq("owner@test.com"), eq(NotificationType.RENTAL_RECEIVED),
                any(), any(), any(), eq(RENTAL_ID));
    }

    @Test
    void 장비_정보가_없으면_수령확인_이벤트를_무시한다() {
        when(rentalQueryPort.find(RENTAL_ID)).thenReturn(Optional.of(rental()));
        when(equipmentQueryPort.findOwnerId(EQUIPMENT_ID)).thenReturn(Optional.empty());

        listener.onRentalReceived(new RentalReceivedEvent(RENTAL_ID));

        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }
}
