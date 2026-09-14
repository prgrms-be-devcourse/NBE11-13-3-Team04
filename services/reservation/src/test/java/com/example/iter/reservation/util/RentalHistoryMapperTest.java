package com.example.iter.reservation.util;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.common.security.Role;
import com.example.iter.common.security.UserStatus;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.api.RentalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RentalHistoryMapperTest {

    private final RentalHistoryMapper rentalHistoryMapper = new RentalHistoryMapper();

    @Test
    void 거래와_상대방_정보를_대여_이력_응답으로_변환한다() {
        Rental rental = Rental.builder()
                .id(100L)
                .equipmentId(10L)
                .renterId(1L)
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 5))
                .productNameSnapshot("예약 당시 맥북")
                .categorySnapshot("노트북")
                .dailyPriceSnapshot(BigDecimal.valueOf(30_000))
                .rentalDays(5)
                .totalPrice(BigDecimal.valueOf(150_000))
                .status(RentalStatus.RETURNING)
                .build();
        UserSummary counterparty = new UserSummary(2L, "등록자닉네임");

        var response = rentalHistoryMapper.toResponse(
                rental,
                counterparty,
                "https://example.com/thumbnail.jpg",
                3
        );

        assertThat(response.rentalId()).isEqualTo(100L);
        assertThat(response.equipmentId()).isEqualTo(10L);
        assertThat(response.equipmentName()).isEqualTo("예약 당시 맥북");
        assertThat(response.thumbnailUrl()).isEqualTo("https://example.com/thumbnail.jpg");
        assertThat(response.counterparty().userId()).isEqualTo(2L);
        assertThat(response.counterparty().nickName()).isEqualTo("등록자닉네임");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(response.totalPrice()).isEqualByComparingTo("150000");
        assertThat(response.status()).isEqualTo(RentalStatus.RETURNING);
        assertThat(response.overdueDays()).isEqualTo(3);
    }

    @Test
    void 썸네일이_없으면_null을_그대로_반환한다() {
        Rental rental = Rental.builder()
                .id(101L)
                .equipmentId(11L)
                .renterId(1L)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 10))
                .productNameSnapshot("장비")
                .dailyPriceSnapshot(BigDecimal.valueOf(10_000))
                .rentalDays(1)
                .totalPrice(BigDecimal.valueOf(10_000))
                .status(RentalStatus.COMPLETED)
                .build();
        UserSummary counterparty = new UserSummary(3L, "대여자닉네임");

        var response = rentalHistoryMapper.toResponse(rental, counterparty, null, 0);

        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.overdueDays()).isZero();
    }
}
