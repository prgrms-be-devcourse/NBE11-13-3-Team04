package com.example.iter.reservation.support;

import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.repository.RentalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 상태 정책(어떤 상태가 "성사된 거래"이고 "탈퇴를 막는가")이 auth/device 에서
// reservation 으로 옮겨왔다. 호출부 테스트가 지키던 보장을 여기서 이어받는다.
@ExtendWith(MockitoExtension.class)
class JpaRentalQueryAdapterTest {

    private static final Long USER_ID = 7L;

    @Mock
    private RentalRepository rentalRepository;

    @InjectMocks
    private JpaRentalQueryAdapter adapter;

    @Test
    void 성사된_거래_집계는_승인_이후_상태만_포함한다() {
        when(rentalRepository.countByRenterIdAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(3L);
        when(rentalRepository.countByOwnerIdSnapshotAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(4L);
        when(rentalRepository.countByRenterIdAndEndDateBeforeAndStatusIn(eq(USER_ID), any(), anyCollection()))
                .thenReturn(2L);

        var stats = adapter.countUserRentalStats(USER_ID, LocalDate.of(2026, 9, 14));

        assertThat(stats.rentedCount()).isEqualTo(3L);
        assertThat(stats.lentCount()).isEqualTo(4L);
        assertThat(stats.overdueCount()).isEqualTo(2L);

        ArgumentCaptor<Collection<RentalStatus>> established = ArgumentCaptor.captor();
        verify(rentalRepository).countByRenterIdAndStatusIn(eq(USER_ID), established.capture());
        assertThat(established.getValue())
                .contains(RentalStatus.APPROVED, RentalStatus.COMPLETED)
                .doesNotContain(RentalStatus.PENDING);

        ArgumentCaptor<Collection<RentalStatus>> overdue = ArgumentCaptor.captor();
        verify(rentalRepository).countByRenterIdAndEndDateBeforeAndStatusIn(eq(USER_ID), any(), overdue.capture());
        assertThat(overdue.getValue())
                .contains(RentalStatus.RECEIVED, RentalStatus.RENTING)
                .doesNotContain(RentalStatus.COMPLETED);
    }

    @Test
    void 탈퇴_차단은_종료된_거래를_세지_않는다() {
        when(rentalRepository.countByRenterIdAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(0L);
        when(rentalRepository.countByOwnerIdSnapshotAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(0L);

        assertThat(adapter.hasWithdrawalBlockingRental(USER_ID)).isFalse();

        ArgumentCaptor<Collection<RentalStatus>> blocking = ArgumentCaptor.captor();
        verify(rentalRepository).countByRenterIdAndStatusIn(eq(USER_ID), blocking.capture());
        assertThat(blocking.getValue())
                .doesNotContain(RentalStatus.COMPLETED, RentalStatus.REJECTED, RentalStatus.CANCELED);
    }

    // 빌린 것만 없어도 안 된다 — 빌려준 거래가 남아 있으면 탈퇴를 막아야 한다.
    @Test
    void 빌려준_거래만_남아도_탈퇴를_막는다() {
        when(rentalRepository.countByRenterIdAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(0L);
        when(rentalRepository.countByOwnerIdSnapshotAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(1L);

        assertThat(adapter.hasWithdrawalBlockingRental(USER_ID)).isTrue();
    }

    @Test
    void 장비_삭제_차단은_분쟁을_별도로_다룬다() {
        when(rentalRepository.existsByEquipmentIdAndStatusIn(eq(1L), anyCollection())).thenReturn(false);

        assertThat(adapter.hasDeletionBlockingRental(1L)).isFalse();

        ArgumentCaptor<Collection<RentalStatus>> blocking = ArgumentCaptor.captor();
        verify(rentalRepository).existsByEquipmentIdAndStatusIn(eq(1L), blocking.capture());
        // DISPUTED 는 hasDisputedRental 이 ACTIVE_DISPUTE_EXISTS 로 따로 막는다.
        assertThat(blocking.getValue()).doesNotContain(RentalStatus.DISPUTED);
    }
}
