package iter.reservation.util;

import iter.auth.api.UserSummary;
import iter.common.security.Role;
import iter.common.security.UserStatus;
import iter.reservation.domain.entity.Rental;
import iter.reservation.api.RentalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RentalHistoryMapperTest {

    private final RentalHistoryMapper rentalHistoryMapper = new RentalHistoryMapper();

    @Test
    void 거래와_상대방_정보를_대여_이력_응답으로_변환한다() {
        Rental rental = new Rental(
                10L,
                0L,
                1L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 5),
                "예약 당시 맥북",
                BigDecimal.valueOf(30_000),
                5,
                BigDecimal.valueOf(150_000),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RentalStatus.RETURNING,
                "노트북",
                null,
                100L);
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
        Rental rental = new Rental(
                11L,
                0L,
                1L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 10),
                "장비",
                BigDecimal.valueOf(10_000),
                1,
                BigDecimal.valueOf(10_000),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RentalStatus.COMPLETED,
                null,
                null,
                101L);
        UserSummary counterparty = new UserSummary(3L, "대여자닉네임");

        var response = rentalHistoryMapper.toResponse(rental, counterparty, null, 0);

        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.overdueDays()).isZero();
    }
}
