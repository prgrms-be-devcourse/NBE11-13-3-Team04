package com.example.iter.admin.stats.service

import com.example.iter.admin.stats.dto.response.AdminStatsResponse
import com.example.iter.auth.api.UserQueryPort
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.dispute.api.ReportQueryPort
import com.example.iter.payment.api.PaymentQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminStatsService(
    private val userQueryPort: UserQueryPort,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val reportQueryPort: ReportQueryPort,
    private val paymentQueryPort: PaymentQueryPort
) {
    // 각 도메인의 집계 정의를 직접 복제하지 않고 공개된 조회 Port를 통해 관리자 현황을 구성합니다.
    @Transactional(readOnly = true)
    fun getStats(): AdminStatsResponse = AdminStatsResponse.of(
        userQueryPort.count(),
        equipmentQueryPort.count(),
        reportQueryPort.countReceived(),
        paymentQueryPort.count()
    )
}
