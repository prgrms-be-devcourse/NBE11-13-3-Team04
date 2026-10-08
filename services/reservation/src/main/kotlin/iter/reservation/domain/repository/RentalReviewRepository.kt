package iter.reservation.domain.repository

import iter.reservation.domain.entity.RentalReview
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

import java.time.LocalDateTime

interface RentalReviewRepository : JpaRepository<RentalReview, Long> {

    fun existsByRentalIdAndReviewerId(rentalId: Long, reviewerId: Long): Boolean

    fun findAllByRentalId(rentalId: Long): List<RentalReview>

    // offset 대신 keyset(cursor) 방식 — NotificationRepository.findNextByReceiverId와 동일한 패턴.
    @Query(
        """
        select r
        from RentalReview r
        where r.revieweeId = :revieweeId
          and (
                :cursorCreatedAt is null
                or r.createdAt < :cursorCreatedAt
                or (r.createdAt = :cursorCreatedAt and r.id < :cursorId)
              )
        order by r.createdAt desc, r.id desc
        """
    )
    fun findNextByRevieweeId(
        @Param("revieweeId") revieweeId: Long,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<RentalReview>

    @Query(
        """
        select avg(r.rating) as averageRating, count(r) as reviewCount
        from RentalReview r
        where r.revieweeId = :revieweeId
        """
    )
    fun findRatingStatsByRevieweeId(@Param("revieweeId") revieweeId: Long): RatingStats

    // findNextByRevieweeId와 동일한 keyset(cursor) 패턴, 대상만 reviewerId로 바꾼 것.
    @Query(
        """
        select r
        from RentalReview r
        where r.reviewerId = :reviewerId
          and (
                :cursorCreatedAt is null
                or r.createdAt < :cursorCreatedAt
                or (r.createdAt = :cursorCreatedAt and r.id < :cursorId)
              )
        order by r.createdAt desc, r.id desc
        """
    )
    fun findNextByReviewerId(
        @Param("reviewerId") reviewerId: Long,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<RentalReview>

    interface RatingStats {
        val averageRating: Double?
        val reviewCount: Long?
    }
}
