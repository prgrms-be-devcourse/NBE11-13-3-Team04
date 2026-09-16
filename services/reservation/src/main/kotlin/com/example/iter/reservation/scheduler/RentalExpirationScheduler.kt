package com.example.iter.reservation.scheduler

import com.example.iter.reservation.service.RentalService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 결제(confirm)를 30분 안에 완료하지 않은 PENDING 요청을 주기적으로 자동 취소해서 선점을 풀어준다.
@Component
class RentalExpirationScheduler(
    private val rentalService: RentalService,
) {

    @Scheduled(fixedRate = 60_000)
    fun expirePendingRentals() {
        val expiredCount = rentalService.expirePendingRentals()
        if (expiredCount > 0) {
            log.info("30분 초과 미결제 대여요청 {}건 자동 취소", expiredCount)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RentalExpirationScheduler::class.java)
    }
}
