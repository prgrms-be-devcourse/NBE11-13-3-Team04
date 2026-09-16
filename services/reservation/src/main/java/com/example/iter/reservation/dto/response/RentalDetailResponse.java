package com.example.iter.reservation.dto.response;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.payment.api.PaymentStatus;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.api.RentalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RentalDetailResponse(
        Long rentalId,
        RentalEquipmentSnapshotResponse equipment,
        UserSummary owner,
        UserSummary renter,
        LocalDate startDate,
        LocalDate endDate,
        int rentalDays,
        BigDecimal totalPrice,
        PaymentStatus paymentStatus,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address,
        String detailAddress,
        String requestMessage,
        RentalStatus status,
        int overdueDays,
        LocalDateTime createdAt
) {
    // paymentStatus는 아직 결제 전(PENDING) 예약이면 null — 결제 전 상태도 조회 가능해야 하므로 null 허용
    public static RentalDetailResponse of(Rental rental, UserSummary renter, UserSummary owner,
                                           PaymentStatus paymentStatus, int overdueDays,
                                           String thumbnailUrl) {
        return new RentalDetailResponse(
                rental.getId(),
                RentalEquipmentSnapshotResponse.from(rental, thumbnailUrl),
                owner,
                renter,
                rental.getStartDate(),
                rental.getEndDate(),
                rental.getRentalDays(),
                rental.getTotalPrice(),
                paymentStatus,
                rental.getReceiverName(),
                rental.getReceiverPhone(),
                rental.getZipcode(),
                rental.getAddress(),
                rental.getDetailAddress(),
                rental.getRequestMessage(),
                rental.getStatus(),
                overdueDays,
                rental.getCreatedAt()
        );
    }
}
