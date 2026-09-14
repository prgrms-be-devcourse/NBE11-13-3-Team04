package com.example.iter.reservation.domain.policy;

import com.example.iter.reservation.api.RentalStatus;

import java.util.EnumSet;
import java.util.Set;

public final class RentalStatusPolicy {

    private static final Set<RentalStatus> TERMINAL_STATUSES = Set.of(
            RentalStatus.COMPLETED,
            RentalStatus.REJECTED,
            RentalStatus.CANCELED
    );

    private static final Set<RentalStatus> WITHDRAWAL_BLOCKING_STATUSES = Set.copyOf(
            EnumSet.complementOf(EnumSet.of(
                    RentalStatus.COMPLETED,
                    RentalStatus.REJECTED,
                    RentalStatus.CANCELED
            ))
    );

    // DISPUTED는 서비스에서 ACTIVE_DISPUTE_EXISTS로 별도 차단합니다.
    private static final Set<RentalStatus> EQUIPMENT_DELETION_BLOCKING_STATUSES = Set.copyOf(
            EnumSet.complementOf(EnumSet.of(
                    RentalStatus.COMPLETED,
                    RentalStatus.REJECTED,
                    RentalStatus.CANCELED,
                    RentalStatus.DISPUTED
            ))
    );

    // 관리자 회원 상세의 "거래 성사" 집계 기준. 이전에는 auth 가 EnumSet 상수로 들고 있었다.
    private static final Set<RentalStatus> ESTABLISHED_STATUSES = Set.copyOf(
            EnumSet.of(
                    RentalStatus.APPROVED,          // 장비 등록자가 대여 요청을 승인한 상태
                    RentalStatus.SHIPPING,          // 장비를 대여자에게 배송 중인 상태
                    RentalStatus.RECEIVED,          // 대여자가 장비 수령을 확인한 상태
                    RentalStatus.RENTING,           // 장비를 실제로 대여 중인 상태
                    RentalStatus.RETURN_REQUESTED,  // 대여자가 반납을 신청한 상태
                    RentalStatus.RETURNING,         // 장비를 등록자에게 반송 중인 상태
                    RentalStatus.RETURNED,          // 반송이 완료되어 반납 확인 단계인 상태
                    RentalStatus.DISPUTED,          // 반납 과정에서 문제가 발생해 분쟁 중인 상태
                    RentalStatus.COMPLETED          // 반납 확인까지 끝나 거래가 최종 완료된 상태
            )
    );

    // 종료일이 지났을 때 연체로 집계할 상태. 장비가 아직 등록자에게 돌아가지 않은 것만 포함한다.
    private static final Set<RentalStatus> OVERDUE_STATUSES = Set.copyOf(
            EnumSet.of(
                    RentalStatus.RECEIVED,          // 대여자가 장비를 수령했고 아직 반환하지 않은 상태
                    RentalStatus.RENTING,           // 장비를 실제로 대여 중인 상태
                    RentalStatus.RETURN_REQUESTED,  // 반납을 신청했지만 아직 반송하지 않은 상태
                    RentalStatus.RETURNING          // 반송 중이지만 등록자에게 아직 도착하지 않은 상태
            )
    );

    private RentalStatusPolicy() {
    }

    public static Set<RentalStatus> establishedStatuses() {
        return ESTABLISHED_STATUSES;
    }

    public static Set<RentalStatus> overdueStatuses() {
        return OVERDUE_STATUSES;
    }

    public static Set<RentalStatus> terminalStatuses() {
        return TERMINAL_STATUSES;
    }

    public static Set<RentalStatus> withdrawalBlockingStatuses() {
        return WITHDRAWAL_BLOCKING_STATUSES;
    }

    public static Set<RentalStatus> equipmentDeletionBlockingStatuses() {
        return EQUIPMENT_DELETION_BLOCKING_STATUSES;
    }
}
