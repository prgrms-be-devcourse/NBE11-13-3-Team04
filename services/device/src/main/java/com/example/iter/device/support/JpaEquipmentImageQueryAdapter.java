package com.example.iter.device.support;

import com.example.iter.device.api.EquipmentImageInfo;
import com.example.iter.device.api.EquipmentImageQueryPort;
import com.example.iter.device.domain.repository.EquipmentImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaEquipmentImageQueryAdapter implements EquipmentImageQueryPort {

    private final EquipmentImageRepository equipmentImageRepository;
    private final EquipmentImageUrlResolver imageUrlResolver;

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentImageInfo> findAll(Long equipmentId) {
        return equipmentImageRepository.findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
                .stream()
                .map(image -> new EquipmentImageInfo(
                        image.getId(),
                        image.getCaptureView() == null ? null : image.getCaptureView().name(),
                        imageUrlResolver.resolve(image),
                        image.getThumbnail()
                ))
                .toList();
    }
}
