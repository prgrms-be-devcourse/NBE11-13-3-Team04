package com.example.iter.reservation.support

import com.example.iter.reservation.api.RentalInfo
import com.example.iter.reservation.api.RentalQueryPort
import com.example.iter.reservation.api.RentalScheduleItem
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.api.UserRentalStats
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.policy.RentalConflictPolicy
import com.example.iter.reservation.domain.policy.RentalStatusPolicy
import com.example.iter.reservation.domain.repository.RentalRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDate
import java.util.Optional

// reservation/api/RentalQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
//
// 상태 정책(RentalStatusPolicy, RentalConflictPolicy)을 아는 것은 이 클래스까지다.
// 이전에는 auth 와 device 가 이 정책 클래스를 직접 import 해서 상태 집합을
// 리포지토리 인자로 넘겼다.
@Component
class JpaRentalQueryAdapter(
    private val rentalRepository: RentalRepository,
) : RentalQueryPort {

    @Transactional(readOnly = true)
    override fun find(rentalId: Long): Optional<RentalInfo> =
        rentalRepository.findById(rentalId).map { toInfo(it) }

    @Transactional(readOnly = true)
    override fun findAll(rentalIds: Collection<Long>): Map<Long, RentalInfo> {
        if (rentalIds.isEmpty()) {
            return emptyMap()
        }
        return rentalRepository.findAllById(rentalIds)
            .map { toInfo(it) }
            .associateBy { it.rentalId() }
    }

    @Transactional(readOnly = true)
    override fun hasWithdrawalBlockingRental(userId: Long): Boolean {
        val blocking = RentalStatusPolicy.withdrawalBlockingStatuses()
        return rentalRepository.countByRenterIdAndStatusIn(userId, blocking) > 0 ||
            rentalRepository.countByOwnerIdSnapshotAndStatusIn(userId, blocking) > 0
    }

    @Transactional(readOnly = true)
    override fun countUserRentalStats(userId: Long, today: LocalDate): UserRentalStats {
        val established = RentalStatusPolicy.establishedStatuses()
        return UserRentalStats(
            rentalRepository.countByRenterIdAndStatusIn(userId, established),
            rentalRepository.countByOwnerIdSnapshotAndStatusIn(userId, established),
            rentalRepository.countByRenterIdAndEndDateBeforeAndStatusIn(
                userId, today, RentalStatusPolicy.overdueStatuses(),
            ),
        )
    }

    @Transactional(readOnly = true)
    override fun hasDisputedRental(equipmentId: Long): Boolean =
        rentalRepository.existsByEquipmentIdAndStatus(equipmentId, RentalStatus.DISPUTED)

    @Transactional(readOnly = true)
    override fun hasDeletionBlockingRental(equipmentId: Long): Boolean =
        rentalRepository.existsByEquipmentIdAndStatusIn(
            equipmentId, RentalStatusPolicy.equipmentDeletionBlockingStatuses(),
        )

    @Transactional(readOnly = true)
    override fun hasConflictingOccupyingRental(equipmentId: Long, from: LocalDate, to: LocalDate): Boolean =
        rentalRepository.existsConflictingOccupyingRental(
            equipmentId, from, to, RentalConflictPolicy.nonOccupyingStatuses(),
        )

    @Transactional(readOnly = true)
    override fun hasOccupyingRentalOutsidePeriod(equipmentId: Long, from: LocalDate, to: LocalDate): Boolean =
        rentalRepository.existsOccupyingRentalOutsidePeriod(
            equipmentId, from, to, RentalConflictPolicy.nonOccupyingStatuses(),
        )

    @Transactional(readOnly = true)
    override fun findSchedule(equipmentId: Long, from: LocalDate, to: LocalDate): List<RentalScheduleItem> =
        rentalRepository.findEquipmentSchedule(equipmentId, from, to, RentalConflictPolicy.nonScheduledStatuses())
            .map { rental -> RentalScheduleItem(rental.id, rental.startDate, rental.endDate, rental.status) }

    private fun toInfo(rental: Rental): RentalInfo =
        RentalInfo(
            rental.id!!,
            rental.equipmentId,
            rental.ownerIdSnapshot,
            rental.renterId,
            rental.productNameSnapshot,
            rental.rejectReason,
            rental.status,
            rental.totalPrice,
            rental.startDate,
            rental.endDate,
        )
}
