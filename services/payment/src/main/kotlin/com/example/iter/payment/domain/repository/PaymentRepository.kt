package com.example.iter.payment.domain.repository

import com.example.iter.payment.api.PaymentStatus
import com.example.iter.payment.domain.entity.Payment
import com.example.iter.payment.service.model.RentalPaymentStatusRow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByRentalId(rentalId: Long): Optional<Payment>
    fun findByOrderId(orderId: String): Optional<Payment>

    // 받은 대여 요청 목록에 필요한 거래 ID와 결제 상태만 한 번에 조회합니다.
    @Query(
        """
        select new com.example.iter.payment.service.model.RentalPaymentStatusRow(
            payment.rentalId,
            payment.status
        )
        from Payment payment
        where payment.rentalId in :rentalIds
        """,
    )
    fun findStatusesByRentalIdIn(@Param("rentalIds") rentalIds: Collection<Long>): List<RentalPaymentStatusRow>

    // 로그인 사용자의 결제 내역. Payment.renterIdSnapshot(결제 시점 대여자 스냅샷)으로 직접 필터링.
    @Query(
        value = """
            select payment
            from Payment payment
            where payment.renterIdSnapshot = :userId
              and (:status is null or payment.status = :status)
            order by payment.createdAt desc, payment.id desc
            """,
        countQuery = """
            select count(payment.id)
            from Payment payment
            where payment.renterIdSnapshot = :userId
              and (:status is null or payment.status = :status)
            """,
    )
    fun findMyPaymentHistory(
        @Param("userId") userId: Long,
        @Param("status") status: PaymentStatus?,
        pageable: Pageable,
    ): Page<Payment>
}
