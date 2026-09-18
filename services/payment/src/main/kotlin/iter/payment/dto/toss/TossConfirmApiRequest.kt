package iter.payment.dto.toss

@JvmRecord
data class TossConfirmApiRequest(val paymentKey: String, val orderId: String, val amount: Long)
