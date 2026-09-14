package com.example.iter.reservation.dto.response;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.payment.api.PaymentStatus;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.api.RentalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentalReceivedItemResponse(
        Long rentalId,
        Long equipmentId,
        String productNameSnapshot,
        UserSummary renter,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalPrice,
        PaymentStatus paymentStatus,
        String requestMessage,
        RentalStatus status
) {
    public static RentalReceivedItemResponse of(Rental rental, UserSummary renter, PaymentStatus paymentStatus) {
        return new RentalReceivedItemResponse(
                rental.getId(),
                rental.getEquipmentId(),
                rental.getProductNameSnapshot(),
                renter,
                rental.getStartDate(),
                rental.getEndDate(),
                rental.getTotalPrice(),
                paymentStatus,
                rental.getRequestMessage(),
                rental.getStatus()
        );
    }
}
