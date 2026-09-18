package iter.reservation.domain.entity

import iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// rentalId/reviewerId/revieweeId는 각각 reservation/auth 도메인 PK를 값으로만 참조 (도메인 간 결합 최소화 컨벤션).
// 대여자 <-> 장비 등록자가 서로에게 남기는 리뷰 — 대여 1건당 (rental_id, reviewer_id) 쌍은 유일해야 하므로
// 한 사람이 같은 거래에 중복 작성할 수 없고, 두 당사자는 각자 1건씩 남길 수 있다.
@Entity
@Table(
    name = "rental_review",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_rental_review_rental_reviewer",
            columnNames = ["rental_id", "reviewer_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_rental_review_reviewee_created_id",
            columnList = "reviewee_id, created_at DESC, id DESC",
        ),
        Index(
            name = "idx_rental_review_reviewer_created_id",
            columnList = "reviewer_id, created_at DESC, id DESC",
        ),
    ],
)
class RentalReview @JvmOverloads constructor(

    @Column(name = "rental_id", nullable = false)
    val rentalId: Long,

    @Column(name = "reviewer_id", nullable = false)
    val reviewerId: Long,

    @Column(name = "reviewee_id", nullable = false)
    val revieweeId: Long,

    @Column(nullable = false)
    val rating: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseTimeEntity()
