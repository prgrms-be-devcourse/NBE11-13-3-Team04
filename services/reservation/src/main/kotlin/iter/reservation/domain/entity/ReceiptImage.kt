package iter.reservation.domain.entity

import iter.common.entity.BaseCreatedAtEntity
import iter.common.image.CaptureView
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "receipt_image",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_receipt_image_capture_view",
            columnNames = ["receipt_id", "capture_view"],
        ),
    ],
)
class ReceiptImage @JvmOverloads constructor(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    val receipt: Receipt,

    @Column(name = "image_url", nullable = false, length = 500)
    val imageUrl: String,

    @Column(name = "sort_order")
    val sortOrder: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_view", length = 16)
    val captureView: CaptureView? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseCreatedAtEntity()
