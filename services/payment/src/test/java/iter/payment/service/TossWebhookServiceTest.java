package iter.payment.service;

import iter.payment.client.TossPaymentClient;
import iter.payment.domain.entity.Payment;
import iter.payment.api.PaymentStatus;
import iter.payment.domain.repository.PaymentRepository;
import iter.payment.dto.toss.TossConfirmApiResponse;
import iter.payment.dto.toss.TossWebhookData;
import iter.payment.dto.toss.TossWebhookPayload;
import iter.payment.event.PaymentConfirmedEvent;
import iter.reservation.api.RentalInfo;
import iter.reservation.api.RentalStatus;
import iter.reservation.api.RentalCommandPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TossWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RentalCommandPort rentalCommandPort;
    @Mock
    private TossPaymentClient tossPaymentClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TossWebhookService tossWebhookService;

    private Payment pendingPayment(String orderId) {
        Payment payment = new Payment(10L, 2L, BigDecimal.valueOf(100));
        payment.assignOrder(orderId, BigDecimal.valueOf(100));
        return payment;
    }

    @Test
    void PAYMENT_STATUS_CHANGED가_아니면_아무것도_안한다() {
        var payload = new TossWebhookPayload("BILLING_DELETED", null);

        tossWebhookService.handle(payload);

        verify(tossPaymentClient, never()).getPayment(any());
    }

    @Test
    void 검증된_상태가_DONE이면_PENDING_결제를_PAID로_바꾸고_대여상태도_바꾼다() {
        Payment payment = pendingPayment("order-1");
        var payload = new TossWebhookPayload("PAYMENT_STATUS_CHANGED",
                new TossWebhookData("payKey", "order-1", "DONE"));

        when(tossPaymentClient.getPayment("payKey"))
                .thenReturn(new TossConfirmApiResponse("payKey", "order-1", "DONE", "2026-08-18T14:18:34+09:00", 100L));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        RentalInfo confirmedRental = org.mockito.Mockito.mock(RentalInfo.class);
        when(confirmedRental.rentalId()).thenReturn(10L);
        when(rentalCommandPort.markPaymentConfirmed(10L)).thenReturn(Optional.of(confirmedRental));


        tossWebhookService.handle(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey()).isEqualTo("payKey");
        // 대여 상태 전환은 reservation 이 한다. 웹훅은 "결제됐다"만 알린다.
        verify(rentalCommandPort).markPaymentConfirmed(10L);
        verify(eventPublisher).publishEvent(new PaymentConfirmedEvent(10L));
    }

    @Test
    void 이미_PAID된_결제면_다시_반영하지_않는다() {
        Payment payment = pendingPayment("order-1");
        payment.markPaid("payKey", java.time.LocalDateTime.now());
        var payload = new TossWebhookPayload("PAYMENT_STATUS_CHANGED",
                new TossWebhookData("payKey", "order-1", "DONE"));

        when(tossPaymentClient.getPayment("payKey"))
                .thenReturn(new TossConfirmApiResponse("payKey", "order-1", "DONE", "2026-08-18T14:18:34+09:00", 100L));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));

        tossWebhookService.handle(payload);

        verify(rentalCommandPort, never()).markPaymentConfirmed(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 매칭되는_결제가_없으면_예외없이_무시한다() {
        var payload = new TossWebhookPayload("PAYMENT_STATUS_CHANGED",
                new TossWebhookData("payKey", "order-unknown", "DONE"));

        when(tossPaymentClient.getPayment("payKey"))
                .thenReturn(new TossConfirmApiResponse("payKey", "order-unknown", "DONE", "2026-08-18T14:18:34+09:00", 100L));
        when(paymentRepository.findByOrderId("order-unknown")).thenReturn(Optional.empty());

        tossWebhookService.handle(payload);

        verify(rentalCommandPort, never()).markPaymentConfirmed(any());
    }

    @Test
    void 재조회한_상태가_DONE이_아니면_아직_반영하지_않는다() {
        Payment payment = pendingPayment("order-1");
        var payload = new TossWebhookPayload("PAYMENT_STATUS_CHANGED",
                new TossWebhookData("payKey", "order-1", "CANCELED"));

        when(tossPaymentClient.getPayment("payKey"))
                .thenReturn(new TossConfirmApiResponse("payKey", "order-1", "CANCELED", null, 100L));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));

        tossWebhookService.handle(payload);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
