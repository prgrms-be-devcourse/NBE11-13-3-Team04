package com.example.iter.payment.dto.request

import com.example.iter.payment.api.PaymentStatus
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

class AdminPaymentSearchRequest(
    @field:Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
    val keyword: String?,

    val status: PaymentStatus?,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val fromDate: LocalDate?,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val toDate: LocalDate?,

    @field:Size(max = 200, message = "커서는 200자 이하여야 합니다.")
    val cursor: String?,

    size: Int?
) {
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = size ?: 20

    @get:JsonIgnore
    @get:AssertTrue(message = "조회 시작일은 종료일보다 늦을 수 없습니다.")
    val isDateRangeValid: Boolean
        get() = fromDate == null || toDate == null || !fromDate.isAfter(toDate)

    fun keyword(): String? = keyword
    fun status(): PaymentStatus? = status
    fun fromDate(): LocalDate? = fromDate
    fun toDate(): LocalDate? = toDate
    fun cursor(): String? = cursor
    fun size(): Int = size
}
