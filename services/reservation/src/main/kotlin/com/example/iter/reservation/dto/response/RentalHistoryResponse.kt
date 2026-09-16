package com.example.iter.reservation.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.reservation.api.RentalStatus

import java.math.BigDecimal
import java.time.LocalDate

data class RentalHistoryResponse(
    val rentalId: Long?,
    val equipmentId: Long?,
    val equipmentName: String?,
    val thumbnailUrl: String?,
    val counterparty: UserSummary?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val totalPrice: BigDecimal?,
    val status: RentalStatus?,
    val overdueDays: Int,
) {
    // RentalHistoryMapper·RentalHistoryService(자바) 및 여러 테스트가 record 접근자
    // 스타일로 그대로 부른다.
    fun rentalId(): Long? = rentalId
    fun equipmentId(): Long? = equipmentId
    fun equipmentName(): String? = equipmentName
    fun thumbnailUrl(): String? = thumbnailUrl
    fun counterparty(): UserSummary? = counterparty
    fun startDate(): LocalDate? = startDate
    fun endDate(): LocalDate? = endDate
    fun totalPrice(): BigDecimal? = totalPrice
    fun status(): RentalStatus? = status
    fun overdueDays(): Int = overdueDays
}
