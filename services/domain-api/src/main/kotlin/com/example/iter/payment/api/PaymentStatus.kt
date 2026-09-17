package com.example.iter.payment.api

// ERD PAYMENT.status
enum class PaymentStatus {
    PENDING, // 결제 대기
    PAID, // 결제 완료
    REFUNDED, // 환불 완료
    CANCELED, // 결제 취소
    FAILED, // 결제 실패
}
