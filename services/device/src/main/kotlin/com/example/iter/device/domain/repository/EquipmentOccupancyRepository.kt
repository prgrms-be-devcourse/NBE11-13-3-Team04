package com.example.iter.device.domain.repository

import com.example.iter.device.domain.entity.EquipmentOccupancy
import org.springframework.data.jpa.repository.JpaRepository

interface EquipmentOccupancyRepository : JpaRepository<EquipmentOccupancy, Long> {

    fun deleteByRentalId(rentalId: Long)

    fun deleteByRentalIdIn(rentalIds: Collection<Long>)
}
