package iter.payment.service

import iter.payment.domain.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFailureRecorder(
    private val paymentRepository: PaymentRepository,
) {

    // paymentId 는 nullable 로 받는다 — PaymentServiceTest 가 아직 저장 전이라 id 가 없는
    // Payment(Payment.builder() 로 직접 조립, save() 를 거치지 않음)로 이 메서드를 호출한다.
    // 실제 운영 경로에서는 findByRentalId 로 이미 저장된 Payment 만 넘어와 id 가 항상 있다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(paymentId: Long?) {
        val paymentId = paymentId!!
        paymentRepository.findById(paymentId).ifPresent { payment ->
            payment.markFailed()
            log.warn(
                "결제 승인 실패 상태 기록: paymentId={}, rentalId={}, status={}",
                payment.id, payment.rentalId, payment.status,
            )
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(PaymentFailureRecorder::class.java)
    }
}
