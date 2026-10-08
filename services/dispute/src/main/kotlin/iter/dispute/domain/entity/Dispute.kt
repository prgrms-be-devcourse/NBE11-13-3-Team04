package iter.dispute.domain.entity

import iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// ERD DISPUTE 엔티티 (기획서 4-1 #13, 6-2 "분쟁 자동 감지" 참고)
// rentalId/reporterId/respondentId는 각각 reservation/auth 도메인 PK를 값으로만 참조
@Entity
@Table(name = "dispute")
class Dispute @JvmOverloads constructor(
    @Column(name = "rental_id", nullable = false)
    val rentalId: Long,

    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Column(name = "respondent_id", nullable = false)
    val respondentId: Long,

    @Column(nullable = false, length = 50)
    val reason: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: DisputeStatus = DisputeStatus.REPORTED,

    @Enumerated(EnumType.STRING)
    @Column(name = "fault_party", length = 10)
    val faultParty: DisputeFaultParty? = null,

    @Column(name = "admin_memo", columnDefinition = "TEXT")
    val adminMemo: String? = null,

    @Column(name = "resolved_at")
    val resolvedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseTimeEntity()
