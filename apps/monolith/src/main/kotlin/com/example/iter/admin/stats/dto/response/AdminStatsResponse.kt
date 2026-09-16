package com.example.iter.admin.stats.dto.response

import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminStatsResponse(val userCount: Long, val equipmentCount: Long, val unresolvedReportCount: Long, val paymentCount: Long) {
    companion object {
        @JvmStatic
        fun of(userCount: Long, equipmentCount: Long, unresolvedReportCount: Long, paymentCount: Long): AdminStatsResponse = AdminStatsResponse(
            userCount,
            equipmentCount,
            unresolvedReportCount,
            paymentCount
        )
    }
}
