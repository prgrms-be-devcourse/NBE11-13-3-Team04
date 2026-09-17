package com.example.iter.dispute.dto.response

import java.time.LocalDateTime
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminReportDetailResponse(val report: ReportDetailResponse, val adminMemo: String?, val updatedAt: LocalDateTime?)
