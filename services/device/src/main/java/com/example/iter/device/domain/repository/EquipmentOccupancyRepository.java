package com.example.iter.device.domain.repository;

import com.example.iter.device.domain.entity.EquipmentOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface EquipmentOccupancyRepository extends JpaRepository<EquipmentOccupancy, Long> {

    void deleteByRentalId(Long rentalId);

    void deleteByRentalIdIn(Collection<Long> rentalIds);
}
