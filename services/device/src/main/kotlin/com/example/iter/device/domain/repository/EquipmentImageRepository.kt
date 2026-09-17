package com.example.iter.device.domain.repository

import com.example.iter.device.domain.entity.EquipmentImage
import org.springframework.data.jpa.repository.JpaRepository

interface EquipmentImageRepository : JpaRepository<EquipmentImage, Long> {

    fun findByEquipmentIdOrderBySortOrderAsc(equipmentId: Long): List<EquipmentImage>

    fun findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId: Long): List<EquipmentImage>

    // 여러 장비의 썸네일을 한 번에 조회합니다.
    fun findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(
        equipmentIds: Collection<Long>,
    ): List<EquipmentImage>
}
