package com.example.iter.device.support

import com.example.iter.device.api.EquipmentOccupancyCommandPort
import com.example.iter.device.domain.entity.EquipmentOccupancy
import com.example.iter.device.domain.repository.EquipmentOccupancyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDate

// device/api/EquipmentOccupancyCommandPort 의 모놀리스 구현.
//
// !! propagation = MANDATORY 다 !!
// 예약 상태 변경과 같은 트랜잭션에서 커밋되거나 함께 롤백돼야 한다.
// 근거는 auth/support/JpaUserLockAdapter 주석 참고.
@Component
class JpaEquipmentOccupancyCommandAdapter(
    private val equipmentOccupancyRepository: EquipmentOccupancyRepository,
) : EquipmentOccupancyCommandPort {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun markOccupied(
        rentalId: Long,
        equipmentId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
        equipmentOccupancyRepository.save(
            EquipmentOccupancy(equipmentId, rentalId, startDate, endDate)
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun markVacated(rentalId: Long) {
        equipmentOccupancyRepository.deleteByRentalId(rentalId)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun markVacatedAll(rentalIds: Collection<Long>) {
        if (rentalIds.isEmpty()) {
            return
        }
        equipmentOccupancyRepository.deleteByRentalIdIn(rentalIds)
    }
}
