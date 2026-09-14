package com.example.iter.device.support;

import com.example.iter.device.api.EquipmentInfo;
import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.device.domain.entity.Equipment;
import com.example.iter.device.domain.entity.EquipmentStatus;
import com.example.iter.device.domain.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// device/api/EquipmentQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
@Component
@RequiredArgsConstructor
public class JpaEquipmentQueryAdapter implements EquipmentQueryPort {

    private final EquipmentRepository equipmentRepository;

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return equipmentRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findOwnerId(Long equipmentId) {
        return equipmentRepository.findById(equipmentId).map(Equipment::getOwnerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentInfo> find(Long equipmentId) {
        return equipmentRepository.findById(equipmentId).map(JpaEquipmentQueryAdapter::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EquipmentInfo> findAll(Collection<Long> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return Map.of();
        }
        return equipmentRepository.findAllById(equipmentIds).stream()
                .map(JpaEquipmentQueryAdapter::toInfo)
                .collect(Collectors.toMap(EquipmentInfo::equipmentId, Function.identity()));
    }

    // EquipmentStatus 를 불리언 두 개로 낮추는 지점. 이 판단이 device 안에 있어야
    // 상태가 늘어날 때 다른 도메인이 영향을 받지 않는다.
    // 같은 매핑을 JpaEquipmentCommandAdapter 도 쓴다 (락으로 조회한 엔티티 -> 값 객체).
    static EquipmentInfo toInfo(Equipment equipment) {
        return new EquipmentInfo(
                equipment.getId(),
                equipment.getOwnerId(),
                equipment.getName(),
                equipment.getCategory() == null ? null : equipment.getCategory().name(),
                equipment.getDailyPrice(),
                equipment.isActive(),
                equipment.getStatus() == EquipmentStatus.DELETED
        );
    }
}
