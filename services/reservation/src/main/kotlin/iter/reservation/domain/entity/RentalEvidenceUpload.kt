package iter.reservation.domain.entity

import iter.common.entity.BaseCreatedAtEntity
import iter.common.image.CaptureView
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

// 누구에게 어떤 대여 증빙 S3 키를 발급했는지 기록해 임의 키 제출과 재사용을 막습니다.
@Entity
@Table(
    name = "rental_evidence_upload",
    indexes = [
        Index(
            name = "idx_rental_evidence_upload_scope",
            columnList = "rental_id,user_id,phase"
        )
    ]
)
open class RentalEvidenceUpload protected constructor() : BaseCreatedAtEntity() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
        protected set

    @field:Column(name = "rental_id", nullable = false)
    open var rentalId: Long = 0L
        protected set

    @field:Column(name = "user_id", nullable = false)
    open var userId: Long = 0L
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 10)
    open lateinit var phase: EvidenceUploadPhase
        protected set

    @field:Column(name = "object_key", nullable = false, unique = true, length = 500)
    open lateinit var objectKey: String
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "capture_view", nullable = false, length = 16)
    open lateinit var captureView: CaptureView
        protected set

    @field:Column(name = "expected_content_type", nullable = false, length = 50)
    open lateinit var expectedContentType: String
        protected set

    @field:Column(name = "expected_size", nullable = false)
    open var expectedSize: Long = 0L
        protected set

    @field:Column(name = "upload_url_expires_at", nullable = false)
    open lateinit var uploadUrlExpiresAt: LocalDateTime
        protected set

    @field:Column(name = "used_at")
    open var usedAt: LocalDateTime? = null
        protected set

    constructor(
        rentalId: Long,
        userId: Long,
        phase: EvidenceUploadPhase,
        objectKey: String,
        captureView: CaptureView,
        expectedContentType: String,
        expectedSize: Long,
        uploadUrlExpiresAt: LocalDateTime
    ) : this() {
        this.rentalId = rentalId
        this.userId = userId
        this.phase = phase
        this.objectKey = objectKey
        this.captureView = captureView
        this.expectedContentType = expectedContentType
        this.expectedSize = expectedSize
        this.uploadUrlExpiresAt = uploadUrlExpiresAt
    }

    fun isUsed(): Boolean = usedAt != null

    fun use(at: LocalDateTime) {
        check(!isUsed()) { "이미 사용한 증빙 이미지 업로드입니다." }
        usedAt = at
    }
}
