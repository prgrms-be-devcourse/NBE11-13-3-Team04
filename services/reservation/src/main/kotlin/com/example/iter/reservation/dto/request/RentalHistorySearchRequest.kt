package com.example.iter.reservation.dto.request

import com.example.iter.reservation.api.RentalStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

class RentalHistorySearchRequest(
    val status: RentalStatus?,

    @field:Size(max = 100, message = "장비명은 100자 이하여야 합니다.")
    val equipmentName: String?,

    page: Int?,

    size: Int?
) {
    @field:Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    val page: Int = page ?: 0

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = size ?: 20

    fun status(): RentalStatus? = status
    fun equipmentName(): String? = equipmentName
    fun page(): Int = page
    fun size(): Int = size
}
