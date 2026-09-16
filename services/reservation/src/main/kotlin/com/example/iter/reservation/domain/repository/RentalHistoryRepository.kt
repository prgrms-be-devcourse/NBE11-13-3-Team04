package com.example.iter.reservation.domain.repository

import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

import java.time.LocalDate

interface RentalHistoryRepository : Repository<Rental, Long>, JpaSpecificationExecutor<Rental> {

    // findBorrowedHistory는 RentalSpecifications로 조립해서 findAll(spec, pageable)로 호출한다
    // (value/countQuery를 손으로 두 벌 유지하지 않기 위해 Specification으로 전환).

    // 등록자가 소유한 장비의 대여 거래를 상태와 예약 당시 장비명으로 검색합니다.
    // LOCATE를 사용해 %, _를 와일드카드가 아닌 실제 검색 문자로 처리합니다.
    @Query(
        value = """
            select r
            from Rental r
            where r.ownerIdSnapshot = :ownerId
              and (:status is null or r.status = :status)
              and (
                    :equipmentName is null
                    or locate(lower(:equipmentName), lower(r.productNameSnapshot)) > 0
                  )
            """,
        countQuery = """
            select count(r.id)
            from Rental r
            where r.ownerIdSnapshot = :ownerId
              and (:status is null or r.status = :status)
              and (
                    :equipmentName is null
                    or locate(lower(:equipmentName), lower(r.productNameSnapshot)) > 0
                  )
            """,
    )
    fun findLentHistory(
        @Param("ownerId") ownerId: Long,
        @Param("status") status: RentalStatus?,
        @Param("equipmentName") equipmentName: String?,
        pageable: Pageable,
    ): Page<Rental>

    // 대여자가 아직 반납을 마치지 못한 연체 거래를 조회합니다.
    fun findByRenterIdAndEndDateBeforeAndStatusIn(
        renterId: Long,
        today: LocalDate,
        statuses: Collection<RentalStatus>,
        pageable: Pageable,
    ): Page<Rental>

    // 등록자가 빌려준 장비 중 아직 반납되지 않은 연체 거래를 조회합니다.
    fun findByOwnerIdSnapshotAndEndDateBeforeAndStatusIn(
        ownerIdSnapshot: Long,
        today: LocalDate,
        statuses: Collection<RentalStatus>,
        pageable: Pageable,
    ): Page<Rental>
}
