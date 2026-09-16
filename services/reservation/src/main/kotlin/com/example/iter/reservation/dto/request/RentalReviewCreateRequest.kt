package com.example.iter.reservation.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class RentalReviewCreateRequest(
    @field:NotNull
    @field:Min(1)
    @field:Max(5)
    val rating: Int?,

    @field:NotBlank
    @field:Size(max = 1000)
    val content: String?,
) {
    fun rating(): Int? = rating
    fun content(): String? = content
}
