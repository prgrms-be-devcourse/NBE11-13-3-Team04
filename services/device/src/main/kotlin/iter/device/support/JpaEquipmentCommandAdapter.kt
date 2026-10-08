package iter.device.support

import iter.device.api.EquipmentCommandPort
import iter.device.api.EquipmentInfo
import iter.device.api.EquipmentLockPort
import iter.device.domain.entity.EquipmentStatus
import iter.device.domain.repository.EquipmentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDateTime
import java.util.Optional

// device/api 의 장비 변경·잠금 포트 구현.
//
// !! propagation = MANDATORY 다 !!
// 기본값이면 호출자 트랜잭션이 없을 때 자기 트랜잭션을 열어 조용히 커밋하고,
// 락은 리턴 즉시 풀린다. 근거는 auth/support/JpaUserLockAdapter 주석 참고.
//
// !! readOnly 를 붙이지 말 것 !! 쓰기와 락은 읽기 전용 트랜잭션에서 동작하지 않는다.
@Component
class JpaEquipmentCommandAdapter(
    private val equipmentRepository: EquipmentRepository,
) : EquipmentCommandPort, EquipmentLockPort {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun deactivateAllOwnedBy(ownerId: Long, deactivatedAt: LocalDateTime): Int =
        // 원본 호출과 인자·플래그가 같아야 한다. "삭제 상태"의 정의가 device 안으로 들어온 것이 변경점이다.
        equipmentRepository.updateStatusByOwnerId(ownerId, EquipmentStatus.DELETED, deactivatedAt)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockForUpdate(equipmentId: Long): Optional<EquipmentInfo> =
        equipmentRepository.findByIdForUpdate(equipmentId).map(::toInfo)
}
