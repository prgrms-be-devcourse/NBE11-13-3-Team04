package com.example.iter.payment.dto.toss

// paymentKey만 있으면 TossPaymentClient.getPayment()로 재조회해서 검증할 수 있으므로 나머지는 안 받는다.
@JvmRecord
data class TossWebhookData(val paymentKey: String, val orderId: String, val status: String)
