package com.example.iter.device.support;

import com.example.iter.device.api.EquipmentOccupancyCommandPort;
import com.example.iter.device.domain.entity.EquipmentOccupancy;
import com.example.iter.device.domain.repository.EquipmentOccupancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;

// device/api/EquipmentOccupancyCommandPort 의 모놀리스 구현.
//
// !! propagation = MANDATORY 다 !!
// 예약 상태 변경과 같은 트랜잭션에서 커밋되거나 함께 롤백돼야 한다.
// 근거는 auth/support/JpaUserLockAdapter 주석 참고.
@Component
@RequiredArgsConstructor
public class JpaEquipmentOccupancyCommandAdapter implements EquipmentOccupancyCommandPort {

    private final EquipmentOccupancyRepository equipmentOccupancyRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markOccupied(Long rentalId, Long equipmentId, LocalDate startDate, LocalDate endDate) {
        equipmentOccupancyRepository.save(EquipmentOccupancy.builder()
                .rentalId(rentalId)
                .equipmentId(equipmentId)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markVacated(Long rentalId) {
        equipmentOccupancyRepository.deleteByRentalId(rentalId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markVacatedAll(Collection<Long> rentalIds) {
        if (rentalIds.isEmpty()) {
            return;
        }
        equipmentOccupancyRepository.deleteByRentalIdIn(rentalIds);
    }
}
