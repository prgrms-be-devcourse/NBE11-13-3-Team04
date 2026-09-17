package com.example.iter.payment.service

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.payment.api.PaymentStatus
import com.example.iter.payment.client.TossApiException
import com.example.iter.payment.client.TossPaymentClient
import com.example.iter.payment.config.TossProperties
import com.example.iter.payment.domain.entity.Payment
import com.example.iter.payment.domain.repository.PaymentRepository
import com.example.iter.payment.dto.request.PaymentConfirmRequest
import com.example.iter.payment.dto.response.PaymentConfirmResponse
import com.example.iter.payment.dto.response.PaymentReadyResponse
import com.example.iter.payment.event.PaymentConfirmedEvent
import com.example.iter.reservation.api.RentalCommandPort
import com.example.iter.reservation.api.RentalInfo
import com.example.iter.reservation.api.RentalQueryPort
import com.example.iter.reservation.api.RentalStatus
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentService(
    private val rentalQueryPort: RentalQueryPort,
    private val rentalCommandPort: RentalCommandPort,
    private val paymentRepository: PaymentRepository,
    private val tossPaymentClient: TossPaymentClient,
    private val tossProperties: TossProperties,
    private val paymentFailureRecorder: PaymentFailureRecorder,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun ready(rentalId: Long, renterId: Long): PaymentReadyResponse {
        val rental = getPayableRental(rentalId, renterId)

        val alreadyPaid = paymentRepository.findByRentalId(rentalId)
            .filter { payment -> payment.status == PaymentStatus.PAID }
            .isPresent

        if (alreadyPaid) {
            throw CustomException(ErrorCode.PAYMENT_ALREADY_COMPLETED)
        }

        val amount = rental.totalPrice
        val orderId = UUID.randomUUID().toString()

        val payment = paymentRepository.findByRentalId(rentalId)
            .orElseGet {
                Payment(
                    rentalId = rentalId,
                    renterIdSnapshot = rental.renterId,
                    amount = amount,
                )
            }
        payment.assignOrder(orderId, amount)

        val savedPayment = paymentRepository.save(payment)
        log.info(
            "결제 준비 처리: paymentId={}, rentalId={}, renterId={}, status={}",
            savedPayment.id, rentalId, renterId, savedPayment.status,
        )
        return PaymentReadyResponse.of(rental, orderId, amount, tossProperties.clientKey)
    }

    @Transactional
    fun confirm(rentalId: Long, renterId: Long, request: PaymentConfirmRequest): PaymentConfirmResponse {
        val rental = getPayableRental(rentalId, renterId)

        val payment = paymentRepository.findByRentalId(rentalId)
            .orElseThrow { CustomException(ErrorCode.RENTAL_NOT_PAYABLE) }

        if (payment.status == PaymentStatus.PAID) {
            throw CustomException(ErrorCode.PAYMENT_ALREADY_COMPLETED)
        }

        if (payment.orderId != request.orderId) {
            throw CustomException(ErrorCode.TOSS_ORDER_MISMATCH)
        }

        if (payment.amount.compareTo(request.amount) != 0) {
            throw CustomException(ErrorCode.TOSS_AMOUNT_MISMATCH)
        }

        try {
            val tossResponse = tossPaymentClient.confirm(
                request.paymentKey!!, payment.orderId!!, payment.amount, payment.idempotencyKey!!,
            )
            payment.markPaid(tossResponse.paymentKey, tossResponse.approvedAtAsLocalDateTime())
        } catch (e: TossApiException) {
            paymentFailureRecorder.recordFailure(payment.id)
            throw CustomException(ErrorCode.TOSS_PAYMENT_FAILED)
        }

        // 변경 "후" 상태를 받아 응답에 싣는다. 전 상태를 쓰면 rentalStatus 가 PENDING 으로 나간다.
        val confirmedRental = rentalCommandPort.markPaymentConfirmed(rentalId)
            .orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

        eventPublisher.publishEvent(PaymentConfirmedEvent(confirmedRental.rentalId))
        log.info(
            "결제 승인 처리: paymentId={}, rentalId={}, renterId={}, paymentStatus={}, rentalStatus={}",
            payment.id, rentalId, renterId, payment.status, confirmedRental.status,
        )
        return PaymentConfirmResponse.of(confirmedRental, payment)
    }

    // 어떤 에러 코드를 던질지는 결제 쪽 정책이라 여기 남긴다.
    // 포트는 상태만 돌려주고 예외를 고르지 않는다.
    private fun getPayableRental(rentalId: Long, renterId: Long): RentalInfo {
        val rental = rentalQueryPort.find(rentalId)
            .orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

        if (!rental.isRenter(renterId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }

        if (rental.status != RentalStatus.PENDING) {
            throw CustomException(ErrorCode.RENTAL_NOT_PAYABLE)
        }

        return rental
    }

    private companion object {
        private val log = LoggerFactory.getLogger(PaymentService::class.java)
    }
}
