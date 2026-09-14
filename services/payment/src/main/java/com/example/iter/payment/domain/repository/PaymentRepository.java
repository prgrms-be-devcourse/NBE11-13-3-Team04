package com.example.iter.payment.domain.repository;

import com.example.iter.payment.domain.entity.Payment;
import com.example.iter.payment.api.PaymentStatus;
import com.example.iter.payment.service.model.RentalPaymentStatusRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRentalId(Long rentalId);
    Optional<Payment> findByOrderId(String orderId);

    // 받은 대여 요청 목록에 필요한 거래 ID와 결제 상태만 한 번에 조회합니다.
    @Query("""
            select new com.example.iter.payment.service.model.RentalPaymentStatusRow(
                payment.rentalId,
                payment.status
            )
            from Payment payment
            where payment.rentalId in :rentalIds
            """)
    List<RentalPaymentStatusRow> findStatusesByRentalIdIn(
            @Param("rentalIds") Collection<Long> rentalIds
    );

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
                    """
    )
    Page<Payment> findMyPaymentHistory(
            @Param("userId") Long userId,
            @Param("status") PaymentStatus status,
            Pageable pageable
    );
}
