package com.example.iter.payment.domain.entity

import com.example.iter.common.entity.BaseTimeEntity
import com.example.iter.payment.api.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// ERD PAYMENT 엔티티 (mock 결제 — 기획서 4-1 #6, 9-1 참고)
// rentalId는 reservation 도메인 PK를 값으로만 참조 (도메인 간 결합 최소화)
@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "idx_payment_created_id", columnList = "created_at DESC, id DESC"),
        Index(name = "idx_payment_status_created_id", columnList = "status, created_at DESC, id DESC"),
        Index(name = "idx_payment_renter_created_id", columnList = "renter_id_snapshot, created_at DESC, id DESC"),
    ],
)
class Payment @JvmOverloads constructor(
    @Column(name = "rental_id", nullable = false, unique = true)
    val rentalId: Long,

    // 결제 시점 대여자 스냅샷 — reservation 도메인 엔티티를 직접 조인하지 않고도 "내 결제 내역"을 조회하기 위함.
    @Column(name = "renter_id_snapshot", nullable = false)
    val renterIdSnapshot: Long,

    amount: BigDecimal,
    status: PaymentStatus = PaymentStatus.PENDING,
    paidAt: LocalDateTime? = null,
    refundedAt: LocalDateTime? = null,
    orderId: String? = null,
    paymentKey: String? = null,
    idempotencyKey: String? = null,
    cancelIdempotencyKey: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseTimeEntity() {

    @Column(nullable = false)
    var amount: BigDecimal = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = status
        protected set

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = paidAt
        protected set

    @Column(name = "refunded_at")
    var refundedAt: LocalDateTime? = refundedAt
        protected set

    @Column(name = "order_id", unique = true)
    var orderId: String? = orderId
        protected set

    @Column(name = "payment_key")
    var paymentKey: String? = paymentKey
        protected set

    // 토스 Idempotency-Key 헤더용. confirm은 orderId 발급 시점에 고정, cancel은 최초 취소 시도 시점에 고정 —
    // 같은 결제건에 대한 재시도가 항상 같은 키를 써야 토스가 중복 요청을 첫 응답 그대로 돌려준다.
    @Column(name = "idempotency_key")
    var idempotencyKey: String? = idempotencyKey
        protected set

    @Column(name = "cancel_idempotency_key")
    var cancelIdempotencyKey: String? = cancelIdempotencyKey
        protected set

    fun markRefunded() {
        status = PaymentStatus.REFUNDED
        refundedAt = LocalDateTime.now()
    }

    fun assignOrder(orderId: String, amount: BigDecimal) {
        this.orderId = orderId
        this.amount = amount
        this.status = PaymentStatus.PENDING
        this.idempotencyKey = UUID.randomUUID().toString()
    }

    // 취소 API 실패로 트랜잭션이 롤백돼도 다음 시도가 같은 키를 사용하도록 PAID 전환 시 발급한다.
    // 이전에 생성된 PAID 결제를 위해 취소 시점의 방어 경로도 유지한다.
    fun ensureCancelIdempotencyKey(): String {
        if (cancelIdempotencyKey == null) {
            cancelIdempotencyKey = UUID.randomUUID().toString()
        }
        return cancelIdempotencyKey!!
    }

    fun markPaid(paymentKey: String, approveAt: LocalDateTime) {
        this.paymentKey = paymentKey
        this.status = PaymentStatus.PAID
        this.paidAt = approveAt
        if (cancelIdempotencyKey == null) {
            cancelIdempotencyKey = UUID.randomUUID().toString()
        }
    }

    fun markFailed() {
        status = PaymentStatus.FAILED
    }
}
