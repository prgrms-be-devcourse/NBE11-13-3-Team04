package com.example.iter.device.support

import com.example.iter.device.api.EquipmentImageInfo
import com.example.iter.device.api.EquipmentImageQueryPort
import com.example.iter.device.domain.repository.EquipmentImageRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JpaEquipmentImageQueryAdapter(
    private val equipmentImageRepository: EquipmentImageRepository,
    private val imageUrlResolver: EquipmentImageUrlResolver,
) : EquipmentImageQueryPort {

    @Transactional(readOnly = true)
    override fun findAll(equipmentId: Long): List<EquipmentImageInfo> {
        return equipmentImageRepository.findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
            .map { image ->
                EquipmentImageInfo(
                    image.id,
                    image.captureView?.name,
                    imageUrlResolver.resolve(image),
                    image.thumbnail,
                )
            }
    }
}
