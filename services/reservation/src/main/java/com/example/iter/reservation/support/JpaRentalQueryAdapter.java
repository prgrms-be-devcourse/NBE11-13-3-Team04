package com.example.iter.reservation.support;

import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalQueryPort;
import com.example.iter.reservation.api.RentalScheduleItem;
import com.example.iter.reservation.api.UserRentalStats;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.policy.RentalConflictPolicy;
import com.example.iter.reservation.domain.policy.RentalStatusPolicy;
import com.example.iter.reservation.domain.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// reservation/api/RentalQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
//
// 상태 정책(RentalStatusPolicy, RentalConflictPolicy)을 아는 것은 이 클래스까지다.
// 이전에는 auth 와 device 가 이 정책 클래스를 직접 import 해서 상태 집합을
// 리포지토리 인자로 넘겼다.
@Component
@RequiredArgsConstructor
public class JpaRentalQueryAdapter implements RentalQueryPort {

    private final RentalRepository rentalRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<RentalInfo> find(Long rentalId) {
        return rentalRepository.findById(rentalId).map(JpaRentalQueryAdapter::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, RentalInfo> findAll(Collection<Long> rentalIds) {
        if (rentalIds.isEmpty()) {
            return Map.of();
        }
        return rentalRepository.findAllById(rentalIds).stream()
                .map(JpaRentalQueryAdapter::toInfo)
                .collect(Collectors.toMap(RentalInfo::rentalId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasWithdrawalBlockingRental(Long userId) {
        var blocking = RentalStatusPolicy.withdrawalBlockingStatuses();
        return rentalRepository.countByRenterIdAndStatusIn(userId, blocking) > 0
                || rentalRepository.countByOwnerIdSnapshotAndStatusIn(userId, blocking) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public UserRentalStats countUserRentalStats(Long userId, LocalDate today) {
        var established = RentalStatusPolicy.establishedStatuses();
        return new UserRentalStats(
                rentalRepository.countByRenterIdAndStatusIn(userId, established),
                rentalRepository.countByOwnerIdSnapshotAndStatusIn(userId, established),
                rentalRepository.countByRenterIdAndEndDateBeforeAndStatusIn(
                        userId, today, RentalStatusPolicy.overdueStatuses())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDisputedRental(Long equipmentId) {
        return rentalRepository.existsByEquipmentIdAndStatus(equipmentId, RentalStatus.DISPUTED);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDeletionBlockingRental(Long equipmentId) {
        return rentalRepository.existsByEquipmentIdAndStatusIn(
                equipmentId, RentalStatusPolicy.equipmentDeletionBlockingStatuses());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConflictingOccupyingRental(Long equipmentId, LocalDate from, LocalDate to) {
        return rentalRepository.existsConflictingOccupyingRental(
                equipmentId, from, to, RentalConflictPolicy.nonOccupyingStatuses());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOccupyingRentalOutsidePeriod(Long equipmentId, LocalDate from, LocalDate to) {
        return rentalRepository.existsOccupyingRentalOutsidePeriod(
                equipmentId, from, to, RentalConflictPolicy.nonOccupyingStatuses());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentalScheduleItem> findSchedule(Long equipmentId, LocalDate from, LocalDate to) {
        return rentalRepository.findEquipmentSchedule(
                        equipmentId, from, to, RentalConflictPolicy.nonScheduledStatuses())
                .stream()
                .map(rental -> new RentalScheduleItem(
                        rental.getId(),
                        rental.getStartDate(),
                        rental.getEndDate(),
                        rental.getStatus()))
                .toList();
    }

    private static RentalInfo toInfo(Rental rental) {
        return new RentalInfo(
                rental.getId(),
                rental.getEquipmentId(),
                rental.getOwnerIdSnapshot(),
                rental.getRenterId(),
                rental.getProductNameSnapshot(),
                rental.getRejectReason(),
                rental.getStatus(),
                rental.getTotalPrice(),
                rental.getStartDate(),
                rental.getEndDate()
        );
    }
}
