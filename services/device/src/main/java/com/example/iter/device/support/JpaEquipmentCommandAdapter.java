package com.example.iter.device.support;

import com.example.iter.device.api.EquipmentCommandPort;
import com.example.iter.device.api.EquipmentInfo;
import com.example.iter.device.api.EquipmentLockPort;
import com.example.iter.device.domain.entity.EquipmentStatus;
import com.example.iter.device.domain.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

// device/api 의 장비 변경·잠금 포트 구현.
//
// !! propagation = MANDATORY 다 !!
// 기본값이면 호출자 트랜잭션이 없을 때 자기 트랜잭션을 열어 조용히 커밋하고,
// 락은 리턴 즉시 풀린다. 근거는 auth/support/JpaUserLockAdapter 주석 참고.
//
// !! readOnly 를 붙이지 말 것 !! 쓰기와 락은 읽기 전용 트랜잭션에서 동작하지 않는다.
@Component
@RequiredArgsConstructor
public class JpaEquipmentCommandAdapter implements EquipmentCommandPort, EquipmentLockPort {

    private final EquipmentRepository equipmentRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int deactivateAllOwnedBy(Long ownerId, LocalDateTime deactivatedAt) {
        // 원본 호출과 인자·플래그가 같아야 한다. "삭제 상태"의 정의가 device 안으로 들어온 것이 변경점이다.
        return equipmentRepository.updateStatusByOwnerId(ownerId, EquipmentStatus.DELETED, deactivatedAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<EquipmentInfo> lockForUpdate(Long equipmentId) {
        return equipmentRepository.findByIdForUpdate(equipmentId)
                .map(JpaEquipmentQueryAdapter::toInfo);
    }
}
