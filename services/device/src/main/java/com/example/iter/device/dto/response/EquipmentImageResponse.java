package com.example.iter.device.dto.response;

import com.example.iter.common.image.CaptureView;
import com.example.iter.device.domain.entity.EquipmentImage;

public record EquipmentImageResponse(
        Long id,
        String imageUrl,
        CaptureView captureView,
        int sortOrder,
        boolean thumbnail
) {
    public static EquipmentImageResponse from(EquipmentImage image, String resolvedImageUrl) {
        return new EquipmentImageResponse(
                image.getId(),
                resolvedImageUrl,
                image.getCaptureView(),
                image.getSortOrder(),
                image.isThumbnail()
        );
    }
}
