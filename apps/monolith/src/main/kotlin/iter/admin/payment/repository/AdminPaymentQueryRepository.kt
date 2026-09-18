package iter.admin.payment.repository

import iter.admin.payment.model.AdminPaymentDetailRow
import iter.admin.payment.model.AdminPaymentSummaryRow
import iter.payment.api.PaymentStatus
import iter.payment.domain.entity.Payment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface AdminPaymentQueryRepository : Repository<Payment, Long> {
    // 검색어가 없으면 LOCATE 조건 자체를 제외해 createdAt, id 정렬 인덱스를 더 쉽게 사용할 수 있게 합니다.
    @Query(
        """
        select new iter.admin.payment.model.AdminPaymentSummaryRow(
            p.id, p.rentalId, p.orderId, r.renterId,
            u.email, u.name, u.nickname,
            r.productNameSnapshot,
            p.amount,
            p.status, r.status,
            p.paidAt, p.refundedAt, p.createdAt
        )
        from Payment p, Rental r, User u
        where p.rentalId = r.id
          and r.renterId = u.id
          and (:status is null or p.status = :status)
          and (:fromDateTime is null or p.createdAt >= :fromDateTime)
          and (:toDateTimeExclusive is null or p.createdAt < :toDateTimeExclusive)
          and (
                :cursorCreatedAt is null
                or p.createdAt < :cursorCreatedAt
                or (p.createdAt = :cursorCreatedAt and p.id < :cursorId)
              )
        order by p.createdAt desc, p.id desc
        """
    )
    fun searchWithoutKeywordForAdminByCursor(
        @Param("status")
        status: PaymentStatus?,

        @Param("fromDateTime")
        fromDateTime: LocalDateTime?,

        @Param("toDateTimeExclusive")
        toDateTimeExclusive: LocalDateTime?,

        @Param("cursorCreatedAt")
        cursorCreatedAt: LocalDateTime?,

        @Param("cursorId")
        cursorId: Long?,

        pageable: Pageable
    ): List<AdminPaymentSummaryRow>

    // LOCATE를 사용해 %, _도 와일드카드가 아니라 실제 검색 문자로 처리합니다.
    // createdAt이 같은 결제는 ID를 보조 커서로 사용해 다음 페이지의 중복·누락을 막습니다.
    @Query(
        """
        select new iter.admin.payment.model.AdminPaymentSummaryRow(
            p.id, p.rentalId, p.orderId, r.renterId,
            u.email, u.name, u.nickname,
            r.productNameSnapshot,
            p.amount,
            p.status, r.status,
            p.paidAt, p.refundedAt, p.createdAt
        )
        from Payment p, Rental r, User u
        where p.rentalId = r.id
          and r.renterId = u.id
          and (
                :keyword is null
                or locate(lower(:keyword), lower(coalesce(p.orderId, ''))) > 0
                or locate(lower(:keyword), lower(r.productNameSnapshot)) > 0
                or locate(lower(:keyword), lower(u.email)) > 0
                or locate(lower(:keyword), lower(u.name)) > 0
                or locate(lower(:keyword), lower(coalesce(u.nickname, ''))) > 0
              )
          and (:status is null or p.status = :status)
          and (:fromDateTime is null or p.createdAt >= :fromDateTime)
          and (:toDateTimeExclusive is null or p.createdAt < :toDateTimeExclusive)
          and (
                :cursorCreatedAt is null
                or p.createdAt < :cursorCreatedAt
                or (p.createdAt = :cursorCreatedAt and p.id < :cursorId)
              )
        order by p.createdAt desc, p.id desc
        """
    )
    fun searchForAdminByCursor(
        @Param("keyword")
        keyword: String?,

        @Param("status")
        status: PaymentStatus?,

        @Param("fromDateTime")
        fromDateTime: LocalDateTime?,

        @Param("toDateTimeExclusive")
        toDateTimeExclusive: LocalDateTime?,

        @Param("cursorCreatedAt")
        cursorCreatedAt: LocalDateTime?,

        @Param("cursorId")
        cursorId: Long?,

        pageable: Pageable
    ): List<AdminPaymentSummaryRow>

    // 목록 엔티티를 다시 조회하지 않고 상세 화면에 필요한 결제·대여·회원 값만 DTO projection으로 가져옵니다.
    @Query(
        """
        select new iter.admin.payment.model.AdminPaymentDetailRow(
            p.id, p.rentalId, p.orderId, r.renterId,
            u.email, u.name, u.nickname,
            r.equipmentId, r.productNameSnapshot, r.categorySnapshot, r.dailyPriceSnapshot,
            r.startDate, r.endDate, r.rentalDays, r.totalPrice,
            p.amount,
            p.status, r.status,
            p.paidAt, p.refundedAt, p.createdAt, p.updatedAt
        )
        from Payment p, Rental r, User u
        where p.id = :paymentId
          and p.rentalId = r.id
          and r.renterId = u.id
        """
    )
    fun findDetailById(@Param("paymentId") paymentId: Long): Optional<AdminPaymentDetailRow>
}
