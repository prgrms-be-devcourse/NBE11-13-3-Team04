package com.example.iter.reservation.dto.response

import com.example.iter.reservation.domain.entity.RentalReview

import java.time.LocalDateTime

data class RentalReviewResponse(
    val id: Long?,
    val rentalId: Long?,
    val reviewerId: Long?,
    val revieweeId: Long?,
    val rating: Int,
    val content: String?,
    val createdAt: LocalDateTime?,
) {
    fun id(): Long? = id
    fun rentalId(): Long? = rentalId
    fun reviewerId(): Long? = reviewerId
    fun revieweeId(): Long? = revieweeId
    fun rating(): Int = rating
    fun content(): String? = content
    fun createdAt(): LocalDateTime? = createdAt

    companion object {
        @JvmStatic
        fun from(review: RentalReview): RentalReviewResponse =
            RentalReviewResponse(
                review.id,
                review.rentalId,
                review.reviewerId,
                review.revieweeId,
                review.rating,
                review.content,
                review.createdAt,
            )
    }
}
