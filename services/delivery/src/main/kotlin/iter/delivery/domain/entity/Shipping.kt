package iter.delivery.domain.entity

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

// ERD SHIPPING 엔티티 (mock 배송 — 기획서 4-1 #7, #10 참고)
// rentalId는 reservation 도메인 PK를 값으로만 참조 (도메인 간 결합 최소화)
// 하나의 Rental에 출고(OUTBOUND)/반송(RETURN) 두 건이 생길 수 있어 rentalId는 unique가 아니다.
@Entity
@Table(name = "shipping")
class Shipping @JvmOverloads constructor(

    @Column(name = "rental_id", nullable = false)
    val rentalId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: ShippingType,

    @Column(length = 50)
    val carrier: String? = null,

    @Column(name = "tracking_number", length = 50)
    val trackingNumber: String? = null,

    status: ShippingStatus = ShippingStatus.READY,

    @Column(name = "shipped_at")
    val shippedAt: LocalDateTime? = null,

    deliveredAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ShippingStatus = status
        protected set

    @Column(name = "delivered_at")
    var deliveredAt: LocalDateTime? = deliveredAt
        protected set

    // ===== 도메인 메서드 =====

    fun markDelivered(deliveredAt: LocalDateTime) {
        this.status = ShippingStatus.DELIVERED
        this.deliveredAt = deliveredAt
    }
}
