package com.example.iter.reservation.domain.repository

import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

interface RentalRepository : JpaRepository<Rental, Long> {

    // 대여자가 아닌 장비 등록자(owner) 기준으로 조회. Rental.ownerIdSnapshot(예약 시점 소유자 스냅샷)으로 직접 필터링.
    @Query(
        """
        select r
        from Rental r
        where r.ownerIdSnapshot = :ownerId
          and (:status is null or r.status = :status)
        """
    )
    fun findReceivedRentals(
        @Param("ownerId") ownerId: Long,
        @Param("status") status: RentalStatus?,
        pageable: Pageable,
    ): Page<Rental>

    // expirePendingRentals가 만료시킬 대상의 ID. 일괄 UPDATE 전에 먼저 조회해둬야
    // EquipmentOccupancy(점유 프로젝션)를 어떤 rentalId로 비울지 알 수 있다.
    @Query(
        """
        select r.id
        from Rental r
        where r.status = RentalStatus.PENDING
          and r.createdAt < :cutoff
        """
    )
    fun findPendingRentalIdsOlderThan(@Param("cutoff") cutoff: LocalDateTime): List<Long>

    // 결제(#3)를 30분 안에 완료하지 않은 PENDING 요청을 자동 취소해서 선점을 풀어준다.
    @Modifying
    @Query(
        """
        update Rental r
        set r.status = RentalStatus.CANCELED
        where r.status = RentalStatus.PENDING
          and r.createdAt < :cutoff
        """
    )
    fun expirePendingRentals(@Param("cutoff") cutoff: LocalDateTime): Int

    // 해당 회원이 대여자인 성립된 거래 수를 조회합니다.
    fun countByRenterIdAndStatusIn(renterId: Long, statuses: Collection<RentalStatus>): Long

    // 해당 회원이 대여자인 현재 연체 거래 수를 조회합니다.
    fun countByRenterIdAndEndDateBeforeAndStatusIn(
        renterId: Long,
        today: LocalDate,
        statuses: Collection<RentalStatus>,
    ): Long

    // 해당 회원이 소유한 장비에서 발생한 성립된 거래 수를 조회합니다.
    fun countByOwnerIdSnapshotAndStatusIn(ownerIdSnapshot: Long, statuses: Collection<RentalStatus>): Long

    // 등록자가 소유한 장비의 대여 거래 중 반납 최종 확인이 필요한 거래를 조회합니다.
    fun findByOwnerIdSnapshotAndStatus(ownerIdSnapshot: Long, status: RentalStatus, pageable: Pageable): Page<Rental>

    // 동일 거래의 반납 최종 확인이 동시에 처리되지 않도록 거래 행을 비관적 쓰기 락으로 조회합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(rentalId: Long): Optional<Rental>

    /** 제외 상태를 제외하고 선택한 기간과 겹치는 예약 ID를 최대 한 건 조회합니다. */
    @Query(
        """
        select r.id
        from Rental r
        where r.equipmentId = :equipmentId
          and r.status not in :excludedStatuses
          and r.startDate <= :endDate
          and r.endDate >= :startDate
        """
    )
    fun findConflictingOccupyingRentalIds(
        @Param("equipmentId") equipmentId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("excludedStatuses") excludedStatuses: Collection<RentalStatus>,
        pageable: Pageable,
    ): List<Long>

    /** 충돌 행 전체를 세지 않고 첫 번째 ID가 발견되면 조회를 종료합니다. */
    fun existsConflictingOccupyingRental(
        equipmentId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        excludedStatuses: Collection<RentalStatus>,
    ): Boolean =
        findConflictingOccupyingRentalIds(
            equipmentId,
            startDate,
            endDate,
            excludedStatuses,
            PageRequest.of(0, 1),
        ).isNotEmpty()

    /**
     * 겹침 확인용 락 읽기 버전. equipment를 FOR UPDATE로 잠근 뒤에도 이 조회가 일반 SELECT면
     * MySQL REPEATABLE READ 트랜잭션의 최초 스냅샷을 그대로 보므로, 락을 기다리는 동안 다른
     * 트랜잭션이 커밋한 겹치는 예약을 놓칠 수 있다. PESSIMISTIC_READ로 최신 커밋 데이터를
     * 강제로 다시 읽어야 하므로, 이미 equipment 락을 잡은 createRental/approveRental에서만 쓴다
     * (평상시 가용성 조회는 락이 필요 없어 existsConflictingOccupyingRental을 그대로 쓴다).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
        """
        select r
        from Rental r
        where r.equipmentId = :equipmentId
          and r.status not in :excludedStatuses
          and r.startDate <= :endDate
          and r.endDate >= :startDate
        """
    )
    fun findConflictingOccupyingRentalsForUpdate(
        @Param("equipmentId") equipmentId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("excludedStatuses") excludedStatuses: Collection<RentalStatus>,
    ): List<Rental>

    fun existsByEquipmentIdAndStatusIn(equipmentId: Long, statuses: Collection<RentalStatus>): Boolean

    fun existsByEquipmentIdAndStatus(equipmentId: Long, status: RentalStatus): Boolean

    @Query(
        """
        select r.id
        from Rental r
        where r.equipmentId = :equipmentId
          and r.status not in :excludedStatuses
          and (r.startDate < :availableFrom or r.endDate > :availableTo)
        """
    )
    fun findOccupyingRentalOutsidePeriodIds(
        @Param("equipmentId") equipmentId: Long,
        @Param("availableFrom") availableFrom: LocalDate,
        @Param("availableTo") availableTo: LocalDate,
        @Param("excludedStatuses") excludedStatuses: Collection<RentalStatus>,
        pageable: Pageable,
    ): List<Long>

    /** 허용 기간을 벗어난 점유 거래도 첫 번째 ID만 확인합니다. */
    fun existsOccupyingRentalOutsidePeriod(
        equipmentId: Long,
        availableFrom: LocalDate,
        availableTo: LocalDate,
        excludedStatuses: Collection<RentalStatus>,
    ): Boolean =
        findOccupyingRentalOutsidePeriodIds(
            equipmentId,
            availableFrom,
            availableTo,
            excludedStatuses,
            PageRequest.of(0, 1),
        ).isNotEmpty()

    @Query(
        """
        select r
        from Rental r
        where r.equipmentId = :equipmentId
          and r.status not in :excludedStatuses
          and r.startDate <= :to
          and r.endDate >= :from
        order by r.startDate asc, r.endDate asc, r.id asc
        """
    )
    fun findEquipmentSchedule(
        @Param("equipmentId") equipmentId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("excludedStatuses") excludedStatuses: Collection<RentalStatus>,
    ): List<Rental>
}
