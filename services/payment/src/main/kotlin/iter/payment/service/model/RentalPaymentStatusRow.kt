package iter.payment.service.model

import iter.payment.api.PaymentStatus

// 받은 대여 요청 목록에서 사용할 거래별 결제 상태 조회 결과입니다.
data class RentalPaymentStatusRow(
    val rentalId: Long,
    val paymentStatus: PaymentStatus,
)
