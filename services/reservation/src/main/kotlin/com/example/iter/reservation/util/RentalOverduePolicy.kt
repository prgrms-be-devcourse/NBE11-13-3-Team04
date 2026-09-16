package com.example.iter.reservation.util

import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// R1에서는 Rental이 아직 자바(Lombok)라 kotlinc가 javac/Lombok보다 먼저 컴파일되는 같은 모듈
// 컴파일 순서 제약 때문에 이 파일을 코틀린으로 옮기지 못했다(rental.getStatus() 등 Lombok
// getter를 못 봤다). Rental이 R3에서 코틀린으로 바뀌면서 이제 옮긴다.
object RentalOverduePolicy {

    private val OVERDUE_STATUSES: Set<RentalStatus> = java.util.Set.copyOf(
        java.util.EnumSet.of(
            RentalStatus.RECEIVED,
            RentalStatus.RENTING,
            RentalStatus.RETURN_REQUESTED,
            RentalStatus.RETURNING,
        )
    )

    // 아직 반납이 완료되지 않아 연체로 판단할 수 있는 거래 상태를 반환합니다.
    @JvmStatic
    fun statuses(): Set<RentalStatus> = OVERDUE_STATUSES

    // 거래 상태와 종료일을 기준으로 연체 일수를 계산하고, 연체가 아니면 0을 반환합니다.
    @JvmStatic
    fun calculateDays(rental: Rental, today: LocalDate): Int {
        if (!OVERDUE_STATUSES.contains(rental.status) || !today.isAfter(rental.endDate)) {
            return 0
        }

        return Math.toIntExact(ChronoUnit.DAYS.between(rental.endDate, today))
    }
}
