package iter.device.domain.repository

import iter.device.domain.entity.EquipmentOccupancy
import org.springframework.data.jpa.repository.JpaRepository

interface EquipmentOccupancyRepository : JpaRepository<EquipmentOccupancy, Long> {

    fun deleteByRentalId(rentalId: Long)

    fun deleteByRentalIdIn(rentalIds: Collection<Long>)
}
