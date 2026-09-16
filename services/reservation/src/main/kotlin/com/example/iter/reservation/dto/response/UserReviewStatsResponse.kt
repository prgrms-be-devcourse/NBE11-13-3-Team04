package com.example.iter.reservation.dto.response

import com.example.iter.reservation.domain.repository.RentalReviewRepository

data class UserReviewStatsResponse(
    val averageRating: Double,
    val reviewCount: Long,
) {
    companion object {
        @JvmStatic
        fun from(stats: RentalReviewRepository.RatingStats?): UserReviewStatsResponse {
            if (stats == null || stats.reviewCount == null || stats.reviewCount == 0L) {
                return UserReviewStatsResponse(0.0, 0L)
            }
            return UserReviewStatsResponse(stats.averageRating, stats.reviewCount)
        }
    }
}
