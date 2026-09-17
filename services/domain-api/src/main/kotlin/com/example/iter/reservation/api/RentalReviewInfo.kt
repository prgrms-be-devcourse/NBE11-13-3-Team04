package com.example.iter.reservation.api

// 다른 도메인이 후기 한 건을 가리킬 때 쓰는 최소 정보.
// 빌더를 두지 않는 이유는 auth/api/UserProfile 주석 참고.
@JvmRecord
data class RentalReviewInfo(
    val reviewId: Long,
    val rentalId: Long,
    val reviewerId: Long,
    val revieweeId: Long,
    val rating: Int,
)
