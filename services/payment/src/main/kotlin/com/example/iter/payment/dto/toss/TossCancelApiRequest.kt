package com.example.iter.payment.dto.toss

// POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel 요청 바디
// cancelReason은 사유 없는 대여 거절(reason=null)에서 그대로 null이 흘러들어올 수 있다.
@JvmRecord
data class TossCancelApiRequest(val cancelReason: String?)
