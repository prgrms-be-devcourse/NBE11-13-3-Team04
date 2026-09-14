package com.example.iter.device.dto.response;

import com.example.iter.reservation.api.RentalScheduleItem;
import com.example.iter.reservation.api.RentalStatus;

import java.time.LocalDate;

public record RentalScheduleItemResponse(
        Long rentalId,
        LocalDate startDate,
        LocalDate endDate,
        RentalStatus status
) {
    public static RentalScheduleItemResponse from(RentalScheduleItem item) {
        return new RentalScheduleItemResponse(
                item.rentalId(),
                item.startDate(),
                item.endDate(),
                item.status()
        );
    }
}
