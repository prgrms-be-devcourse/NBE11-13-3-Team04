package com.example.iter.reservation.dto.response;

import com.example.iter.reservation.domain.entity.Rental;

import java.math.BigDecimal;

public record RentalEquipmentSnapshotResponse(
        Long equipmentId,
        String equipmentName,
        String category,
        BigDecimal dailyPrice,
        String thumbnailUrl
) {
    // 현재 Equipment가 아니라 예약 시점에 Rental에 복사해둔 스냅샷을 써야 이후 장비 정보가
    // 바뀌어도 과거 예약 조회 결과가 그대로 유지된다.
    // 썸네일은 Rental에 별도 스냅샷이 없으므로 현재 장비 이미지에서 조회한 URL을 함께 받습니다.
    public static RentalEquipmentSnapshotResponse from(Rental rental, String thumbnailUrl) {
        return new RentalEquipmentSnapshotResponse(
                rental.getEquipmentId(),
                rental.getProductNameSnapshot(),
                rental.getCategorySnapshot(),
                rental.getDailyPriceSnapshot(),
                thumbnailUrl
        );
    }
}
