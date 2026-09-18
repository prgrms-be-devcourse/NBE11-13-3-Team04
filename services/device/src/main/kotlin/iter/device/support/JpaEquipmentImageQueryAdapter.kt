package iter.device.support

import iter.device.api.EquipmentImageInfo
import iter.device.api.EquipmentImageQueryPort
import iter.device.domain.repository.EquipmentImageRepository
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
                    image.id!!,
                    image.captureView?.name,
                    imageUrlResolver.resolve(image),
                    image.thumbnail,
                )
            }
    }
}
