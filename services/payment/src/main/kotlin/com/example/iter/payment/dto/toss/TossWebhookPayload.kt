package com.example.iter.payment.dto.toss

// 토스 웹훅 요청 바디. data.status/orderId/amount는 신뢰하지 않고 재검증용으로만 참고한다
// (TossWebhookService가 paymentKey로 토스에 재조회해서 받은 값만 실제로 사용).
@JvmRecord
data class TossWebhookPayload(val eventType: String?, val data: TossWebhookData?) {
    fun isPaymentStatusChanged(): Boolean = eventType == "PAYMENT_STATUS_CHANGED" && data != null
}
