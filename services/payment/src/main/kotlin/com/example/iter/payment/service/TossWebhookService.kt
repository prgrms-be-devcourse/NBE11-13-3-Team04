package com.example.iter.payment.service

import com.example.iter.payment.api.PaymentStatus
import com.example.iter.payment.client.TossPaymentClient
import com.example.iter.payment.domain.repository.PaymentRepository
import com.example.iter.payment.dto.toss.TossWebhookPayload
import com.example.iter.reservation.api.RentalCommandPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 토스 웹훅(POST /api/v1/webhooks/toss)이 실제로 하는 일.
// 웹훅 바디에 있는 값(orderId/status 등)은 위조될 수 있으므로 절대 그대로 믿지 않는다 —
// paymentKey로 토스에 재조회(getPayment)해서 받은 값만 신뢰한다.
@Service
class TossWebhookService(
    private val paymentRepository: PaymentRepository,
    private val rentalCommandPort: RentalCommandPort,
    private val tossPaymentClient: TossPaymentClient,
) {

    @Transactional
    fun handle(payload: TossWebhookPayload) {
        if (!payload.isPaymentStatusChanged()) {
            log.info("토스 웹훅 처리 제외: 지원하지 않는 이벤트 유형")
            return
        }

        val verified = tossPaymentClient.getPayment(payload.data!!.paymentKey)

        val payment = paymentRepository.findByOrderId(verified.orderId).orElse(null)
        if (payment == null) {
            log.warn("토스 웹훅 처리 실패: 매칭되는 결제 내역 없음")
            return
        }

        if (verified.status != "DONE") {
            log.info(
                "토스 웹훅 처리 제외: paymentId={}, rentalId={}, providerStatus={}",
                payment.id, payment.rentalId, verified.status,
            )
            return
        }

        if (payment.status == PaymentStatus.PAID) {
            log.info(
                "토스 웹훅 중복 처리 방지: paymentId={}, rentalId={}, status={}",
                payment.id, payment.rentalId, payment.status,
            )
            return
        }

        // confirm() 응답보다 웹훅이 먼저 도착하는 경우를 대비한 방어적 반영
        payment.markPaid(verified.paymentKey, verified.approvedAtAsLocalDateTime())
        // 대여가 없으면 조용히 넘어간다 — 예외를 던지면 500 이 나가고 토스가 재시도한다.
        rentalCommandPort.markPaymentConfirmed(payment.rentalId)
        log.info(
            "토스 웹훅 결제 상태 반영 처리: paymentId={}, rentalId={}, status={}",
            payment.id, payment.rentalId, payment.status,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TossWebhookService::class.java)
    }
}
