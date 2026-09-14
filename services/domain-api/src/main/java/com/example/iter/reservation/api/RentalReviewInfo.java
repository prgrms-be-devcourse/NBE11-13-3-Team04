package com.example.iter.reservation.api;

import java.util.Objects;

// 다른 도메인이 후기 한 건을 가리킬 때 쓰는 최소 정보.
// 빌더를 두지 않는 이유는 auth/api/UserProfile 주석 참고.
public record RentalReviewInfo(
        Long reviewId,
        Long rentalId,
        Long reviewerId,
        Long revieweeId,
        int rating
) {
    public RentalReviewInfo {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(rentalId, "rentalId");
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(revieweeId, "revieweeId");
    }
}
