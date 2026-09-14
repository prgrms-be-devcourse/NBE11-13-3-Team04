package com.example.iter.reservation.api;

import java.time.LocalDate;
import java.util.Objects;

// 장비의 예약 일정 한 칸. device 의 일정 조회 화면이 유일한 소비자다.
public record RentalScheduleItem(
        Long rentalId,
        LocalDate startDate,
        LocalDate endDate,
        RentalStatus status
) {
    public RentalScheduleItem {
        Objects.requireNonNull(rentalId, "rentalId");
        Objects.requireNonNull(status, "status");
    }
}
