package com.example.iter.device.api;

// 다른 도메인이 장비 사진을 조회할 때 사용하는 최소 정보입니다.
// 촬영 방향은 공용 enum 결합을 피하기 위해 이름으로 전달하며, 기존 미분류 사진은 null입니다.
public record EquipmentImageInfo(
        Long imageId,
        String captureView,
        String imageUrl,
        boolean thumbnail
) {
}
