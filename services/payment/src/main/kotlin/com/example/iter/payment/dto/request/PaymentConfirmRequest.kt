package com.example.iter.payment.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

@JvmRecord
data class PaymentConfirmRequest(
    @field:NotBlank(message = "paymentKey는 필수입니다.")
    val paymentKey: String?,

    @field:NotBlank(message = "orderId는 필수입니다.")
    val orderId: String?,

    @field:NotNull(message = "amount는 필수입니다.")
    @field:Positive(message = "amount는 0보다 커야 합니다.")
    val amount: BigDecimal?,
)
