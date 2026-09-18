package iter.payment.support;

import iter.payment.domain.entity.Payment;
import iter.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaPaymentQueryAdapterTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private JpaPaymentQueryAdapter adapter;

    // 포트 주석의 "상태로 거르지 않는다 (취소·환불 포함)"를 지키는 테스트.
    @Test
    void 전체_결제_건수를_거르지_않고_그대로_돌려준다() {
        when(paymentRepository.count()).thenReturn(300L);

        assertThat(adapter.count()).isEqualTo(300L);

        verify(paymentRepository).count();
        verifyNoMoreInteractions(paymentRepository);
    }

    // "환불됨 = REFUNDED" 판단이 알림 리스너에서 이 어댑터로 옮겨왔다.
    @Test
    void 환불된_결제면_true를_돌려준다() {
        Payment refunded = new Payment(10L, 2L, BigDecimal.valueOf(150000));
        refunded.markRefunded();
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(refunded));

        assertThat(adapter.isRefundedForRental(10L)).isTrue();
    }

    @Test
    void 환불되지_않은_결제면_false를_돌려준다() {
        Payment paid = new Payment(10L, 2L, BigDecimal.valueOf(150000));
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.of(paid));

        assertThat(adapter.isRefundedForRental(10L)).isFalse();
    }

    // 결제 기록 자체가 없는 경우. 예외를 던지면 알림이 통째로 막힌다.
    @Test
    void 결제_기록이_없으면_false를_돌려준다() {
        when(paymentRepository.findByRentalId(10L)).thenReturn(Optional.empty());

        assertThat(adapter.isRefundedForRental(10L)).isFalse();
    }
}
