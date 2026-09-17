package com.example.iter.reservation.api

import java.time.LocalDate

// 장비의 예약 일정 한 칸. device 의 일정 조회 화면이 유일한 소비자다.
@JvmRecord
data class RentalScheduleItem(
    val rentalId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: RentalStatus,
)
