package com.example.iter.payment.support

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.payment.api.PaymentCommandPort
import com.example.iter.payment.api.PaymentStatus
import com.example.iter.payment.client.TossApiException
import com.example.iter.payment.client.TossPaymentClient
import com.example.iter.payment.domain.repository.PaymentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

// payment/api/PaymentCommandPort 의 모놀리스 구현.
//
// !! propagation = MANDATORY 다 !!
// 호출자(RentalService.cancelRental / rejectRental)의 트랜잭션에 참여해야
// 환불 기록과 대여 상태 변경이 함께 커밋되거나 함께 롤백된다.
//
// 토스 취소 호출이 트랜잭션 안에 있는 것은 기존 동작 그대로다. 옮기면서 바꾸지 않았다 —
// 외부 호출을 트랜잭션 밖으로 빼는 것은 성격이 다른 변경이라 별도로 다뤄야 한다.
@Component
class JpaPaymentCommandAdapter(
    private val paymentRepository: PaymentRepository,
    private val tossPaymentClient: TossPaymentClient,
) : PaymentCommandPort {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun cancelIfPaid(rentalId: Long?, reason: String?): Optional<PaymentStatus> {
        val rentalId = rentalId!!
        val payment = paymentRepository.findByRentalId(rentalId).orElse(null)
            ?: return Optional.empty()
        if (payment.status != PaymentStatus.PAID) {
            return Optional.of(payment.status)
        }

        try {
            // 멱등키 생성은 payment 의 규칙이다. 이전에는 reservation 이 이걸 트리거했다.
            tossPaymentClient.cancel(payment.paymentKey!!, reason, payment.ensureCancelIdempotencyKey())
            payment.markRefunded()
        } catch (e: TossApiException) {
            // 토스 취소가 안 됐는데 대여만 취소되면 돈과 상태가 어긋난다. 예외를 그대로 올린다.
            throw CustomException(ErrorCode.TOSS_PAYMENT_FAILED)
        }
        // 변경 "후" 상태 — 호출부 응답이 PAID 를 싣지 않도록.
        return Optional.of(payment.status)
    }
}
