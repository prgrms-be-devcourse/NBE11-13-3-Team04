package iter.payment.dto.request

import iter.payment.api.PaymentStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

class PaymentHistorySearchRequest(
    val status: PaymentStatus?,
    page: Int?,
    size: Int?,
) {
    @field:Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    val page: Int = page ?: 0

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = size ?: 20
}
