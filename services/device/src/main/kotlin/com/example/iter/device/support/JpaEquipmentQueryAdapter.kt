package com.example.iter.device.support

import com.example.iter.device.api.EquipmentInfo
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.device.domain.entity.Equipment
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.repository.EquipmentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import java.util.Optional

// device/api/EquipmentQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
@Component
class JpaEquipmentQueryAdapter(
    private val equipmentRepository: EquipmentRepository,
) : EquipmentQueryPort {

    @Transactional(readOnly = true)
    override fun count(): Long = equipmentRepository.count()

    @Transactional(readOnly = true)
    override fun findOwnerId(equipmentId: Long): Optional<Long> =
        equipmentRepository.findById(equipmentId).map { it.ownerId }

    @Transactional(readOnly = true)
    override fun find(equipmentId: Long): Optional<EquipmentInfo> =
        equipmentRepository.findById(equipmentId).map(::toInfo)

    @Transactional(readOnly = true)
    override fun findAll(equipmentIds: Collection<Long>): Map<Long, EquipmentInfo> {
        if (equipmentIds.isEmpty()) {
            return emptyMap()
        }
        return equipmentRepository.findAllById(equipmentIds)
            .map(::toInfo)
            .associateBy { it.equipmentId }
    }
}

// EquipmentStatus 를 불리언 두 개로 낮추는 지점. 이 판단이 device 안에 있어야
// 상태가 늘어날 때 다른 도메인이 영향을 받지 않는다.
// 같은 매핑을 JpaEquipmentCommandAdapter 도 쓴다 (락으로 조회한 엔티티 -> 값 객체).
//
// 클래스 안의 static 이 아니라 톱레벨 함수다. 코틀린 companion object 멤버는 다른
// 파일에서 `클래스::메서드` 참조로 못 부르는데, JpaEquipmentCommandAdapter 가 그렇게 쓴다.
internal fun toInfo(equipment: Equipment): EquipmentInfo = EquipmentInfo(
    equipment.id!!,
    equipment.ownerId,
    equipment.name,
    equipment.category.name,
    equipment.dailyPrice,
    equipment.isActive(),
    equipment.status == EquipmentStatus.DELETED,
)
