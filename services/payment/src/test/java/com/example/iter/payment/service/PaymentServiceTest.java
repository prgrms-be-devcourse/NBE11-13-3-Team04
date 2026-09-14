package com.example.iter.payment.service;

import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.payment.client.TossApiException;
import com.example.iter.payment.client.TossPaymentClient;
import com.example.iter.payment.config.TossProperties;
import com.example.iter.payment.domain.entity.Payment;
import com.example.iter.payment.api.PaymentStatus;
import com.example.iter.payment.domain.repository.PaymentRepository;
import com.example.iter.payment.dto.request.PaymentConfirmRequest;
import com.example.iter.payment.dto.toss.TossConfirmApiResponse;
import com.example.iter.payment.event.PaymentConfirmedEvent;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.api.RentalCommandPort;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private RentalQueryPort rentalQueryPort;

    @Mock
    private RentalCommandPort rentalCommandPort;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TossPaymentClient tossPaymentClient;
    @Mock
    private PaymentFailureRecorder paymentFailureRecorder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final TossProperties tossProperties = new TossProperties("test_ck_docs", "test_sk_docs");

    private PaymentService paymentService() {
        return new PaymentService(rentalQueryPort, rentalCommandPort, paymentRepository, tossPaymentClient, tossProperties, paymentFailureRecorder, eventPublisher);
    }

    private RentalInfo pendingRental() {
        return rental(RentalStatus.PENDING);
    }

    private RentalInfo rental(RentalStatus status) {
        return new RentalInfo(10L, 1L, 2L, "소니 A7C2", null, status, BigDecimal.valueOf(180000),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
    }

    private Payment readyPayment(String orderId, BigDecimal amount) {
        Payment payment = Payment.builder().rentalId(10L).amount(amount).build();
        payment.assignOrder(orderId, amount);
        return payment;
    }

    @Test
    void ready는_orderId를_발급하고_Payment를_PENDING으로_저장한다() {
        when(rentalQueryPort.find(10L)).thenReturn(Optional.of(pendingRental()));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService().ready(10L, 2L);

        assertThat(response.rentalId()).isEqualTo(10L);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(180000));
        assertThat(response.orderId()).isNotBlank();
    }

    @Test
    void confirm은_승인_성공시_Rental과_Payment_상태를_바꾼다() {
        Payment payment = readyPayment("order-1", BigDecimal.valueOf(180000));
        when(rentalQueryPort.find(10L)).thenReturn(Optional.of(pendingRental()));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.confirm(eq("payKey"), eq("order-1"), eq(BigDecimal.valueOf(180000)), any()))
                .thenReturn(new TossConfirmApiResponse("payKey", "order-1", "DONE", "2026-08-10T09:10:00+09:00", 180000L));

        // 커맨드 포트는 변경 "후" 상태를 돌려준다. 응답의 rentalStatus 가 이 값이어야 한다.
        when(rentalCommandPort.markPaymentConfirmed(10L))
                .thenReturn(Optional.of(rental(RentalStatus.REQUESTED)));

        var request = new PaymentConfirmRequest("payKey", "order-1", BigDecimal.valueOf(180000));
        var response = paymentService().confirm(10L, 2L, request);

        assertThat(response.rentalStatus()).isEqualTo(RentalStatus.REQUESTED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey()).isEqualTo("payKey");
        verify(eventPublisher).publishEvent(new PaymentConfirmedEvent(10L));
    }

    @Test
    void confirm은_금액이_다르면_토스를_호출하지_않고_예외를_던진다() {
        Payment payment = readyPayment("order-1", BigDecimal.valueOf(180000));
        when(rentalQueryPort.find(10L)).thenReturn(Optional.of(pendingRental()));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(payment));

        var request = new PaymentConfirmRequest("payKey", "order-1", BigDecimal.valueOf(999));

        assertThatThrownBy(() -> paymentService().confirm(10L, 2L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOSS_AMOUNT_MISMATCH);
        verify(tossPaymentClient, never()).confirm(any(), any(), any(), any());
    }

    @Test
    void confirm은_orderId가_다르면_예외를_던진다() {
        Payment payment = readyPayment("order-1", BigDecimal.valueOf(180000));
        when(rentalQueryPort.find(10L)).thenReturn(Optional.of(pendingRental()));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(payment));

        var request = new PaymentConfirmRequest("payKey", "order-다른값", BigDecimal.valueOf(180000));

        assertThatThrownBy(() -> paymentService().confirm(10L, 2L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOSS_ORDER_MISMATCH);
    }

    @Test
    void confirm은_이미_결제완료된_건이면_예외를_던진다() {
        Payment payment = readyPayment("order-1", BigDecimal.valueOf(180000));
        payment.markPaid("payKey", java.time.LocalDateTime.now());
        when(rentalQueryPort.find(10L)).thenReturn(Optional.of(pendingRental()));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(payment));

        var request = new PaymentConfirmRequest("payKey", "order-1", BigDecimal.valueOf(180000));

        assertThatThrownBy(() -> paymentService().confirm(10L, 2L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_COMPLETED);
    }

    @Test
    void confirm은_토스_승인_실패시_실패기록을_남기고_예외를_던진다() {
        Payment payment = readyPayment("order-1", BigDecimal.valueOf(180000));
        when(rentalQueryPort.find(10L)).thenReturn(Optional.of(pendingRental()));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(payment));
        when(tossPaymentClient.confirm(any(), any(), any(), any())).thenThrow(new TossApiException("REJECT_CARD_COMPANY"));

        var request = new PaymentConfirmRequest("payKey", "order-1", BigDecimal.valueOf(180000));

        assertThatThrownBy(() -> paymentService().confirm(10L, 2L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOSS_PAYMENT_FAILED);
        verify(paymentFailureRecorder).recordFailure(payment.getId());
        // 토스 승인이 실패하면 대여 상태를 바꾸지 않는다.
        verify(rentalCommandPort, never()).markPaymentConfirmed(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
