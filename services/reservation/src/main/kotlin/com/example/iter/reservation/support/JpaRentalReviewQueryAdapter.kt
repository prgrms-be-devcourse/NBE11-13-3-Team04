package com.example.iter.reservation.support

import com.example.iter.reservation.api.RentalReviewInfo
import com.example.iter.reservation.api.RentalReviewQueryPort
import com.example.iter.reservation.domain.entity.RentalReview
import com.example.iter.reservation.domain.repository.RentalReviewRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import java.util.Optional

// reservation/api/RentalReviewQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
@Component
class JpaRentalReviewQueryAdapter(
    private val rentalReviewRepository: RentalReviewRepository,
) : RentalReviewQueryPort {

    @Transactional(readOnly = true)
    override fun find(reviewId: Long): Optional<RentalReviewInfo> =
        rentalReviewRepository.findById(reviewId).map { toInfo(it) }

    private fun toInfo(review: RentalReview): RentalReviewInfo =
        RentalReviewInfo(review.id!!, review.rentalId, review.reviewerId, review.revieweeId, review.rating)
}
