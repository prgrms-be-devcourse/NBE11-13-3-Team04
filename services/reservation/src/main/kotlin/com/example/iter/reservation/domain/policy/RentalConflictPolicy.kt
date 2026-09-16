package com.example.iter.reservation.domain.policy

import com.example.iter.reservation.api.RentalStatus

/** 대여 생성·조회와 승인 단계에서 사용하는 예약 기간 충돌 정책을 정의합니다. */
// java.util.Set.of 를 쓰는 이유는 RentalStatusPolicy.kt 의 주석과 같다 — 진짜 불변 집합이
// 되어야 자바 호출부가 실수로 이 상수 집합을 변형해도 조용히 넘어가지 않는다.
object RentalConflictPolicy {

    private val NON_OCCUPYING_STATUSES: Set<RentalStatus> = java.util.Set.of(
        RentalStatus.REJECTED,
        RentalStatus.CANCELED,
        RentalStatus.COMPLETED,
    )

    private val NON_CONFIRMED_STATUSES: Set<RentalStatus> = java.util.Set.of(
        RentalStatus.PENDING,
        RentalStatus.REQUESTED,
        RentalStatus.REJECTED,
        RentalStatus.CANCELED,
    )

    private val NON_SCHEDULED_STATUSES: Set<RentalStatus> = java.util.Set.of(
        RentalStatus.PENDING,
        RentalStatus.REQUESTED,
        RentalStatus.REJECTED,
        RentalStatus.CANCELED,
        RentalStatus.COMPLETED,
    )

    @JvmStatic
    fun nonOccupyingStatuses(): Set<RentalStatus> = NON_OCCUPYING_STATUSES

    @JvmStatic
    fun nonConfirmedStatuses(): Set<RentalStatus> = NON_CONFIRMED_STATUSES

    @JvmStatic
    fun nonScheduledStatuses(): Set<RentalStatus> = NON_SCHEDULED_STATUSES
}
