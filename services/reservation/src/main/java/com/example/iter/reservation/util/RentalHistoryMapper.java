package com.example.iter.reservation.util;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.dto.response.RentalHistoryResponse;
import org.springframework.stereotype.Component;

@Component
public class RentalHistoryMapper {

    // 대여 거래와 상대방 정보를 대여 이력 목록 응답으로 변환합니다.
    public RentalHistoryResponse toResponse(
            Rental rental,
            UserSummary counterparty,
            String thumbnailUrl,
            int overdueDays
    ) {
        return new RentalHistoryResponse(
                rental.getId(),
                rental.getEquipmentId(),
                rental.getProductNameSnapshot(),
                thumbnailUrl,
                counterparty,
                rental.getStartDate(),
                rental.getEndDate(),
                rental.getTotalPrice(),
                rental.getStatus(),
                overdueDays
        );
    }
}
