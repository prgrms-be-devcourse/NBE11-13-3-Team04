package com.example.iter.reservation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ShippingRegisterRequest(
    @field:NotBlank(message = "택배사는 필수입니다.")
    @field:Size(max = 50, message = "택배사는 50자 이하여야 합니다.")
    val carrier: String?,

    @field:NotBlank(message = "운송장 번호는 필수입니다.")
    @field:Size(max = 50, message = "운송장 번호는 50자 이하여야 합니다.")
    val trackingNumber: String?,
) {
    fun carrier(): String? = carrier
    fun trackingNumber(): String? = trackingNumber
}
