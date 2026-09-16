package com.example.iter.device.support

import com.example.iter.device.api.EquipmentThumbnailQueryPort
import com.example.iter.device.domain.repository.EquipmentImageRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// device/api/EquipmentThumbnailQueryPort 의 모놀리스 구현.
//
// image.equipment.id 호출이 여기 있는 것은 안전하다 — device 안이고,
// EquipmentImage 와 Equipment 는 같은 도메인의 @ManyToOne 연관이다.
// 이전에는 이 코드가 reservation 에 있어서 LAZY 프록시가 도메인 경계를 넘었다.
//
// putIfAbsent 는 기존 동작을 보존한다. 쿼리가 sortOrder, id 오름차순으로 정렬해
// 돌려주므로 장비마다 첫 번째 썸네일이 대표가 된다.
@Component
class JpaEquipmentThumbnailQueryAdapter(
    private val equipmentImageRepository: EquipmentImageRepository,
) : EquipmentThumbnailQueryPort {

    @Transactional(readOnly = true)
    override fun findThumbnailUrls(equipmentIds: Collection<Long>): Map<Long, String> {
        if (equipmentIds.isEmpty()) {
            return emptyMap()
        }

        val thumbnails = LinkedHashMap<Long, String>()
        equipmentImageRepository
            .findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(equipmentIds)
            // DB 에서 읽은 엔티티라 id 는 항상 채워져 있다.
            .forEach { image -> thumbnails.putIfAbsent(image.equipment.id!!, image.imageUrl) }
        return thumbnails
    }
}
