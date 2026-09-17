package com.example.iter.delivery.support

import com.example.iter.delivery.api.ShippingCommandPort
import com.example.iter.delivery.domain.entity.Shipping
import com.example.iter.delivery.domain.entity.ShippingStatus
import com.example.iter.delivery.domain.entity.ShippingType
import com.example.iter.delivery.domain.repository.ShippingRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// delivery/api/ShippingCommandPort 의 모놀리스 구현.
// 엔티티 조립이 여기로 들어왔다 — 호출부는 이제 ShippingType/ShippingStatus 를 모른다.
//
// !! propagation = MANDATORY 다 !!
// 배송 기록과 대여 상태 변경이 함께 커밋되거나 함께 롤백돼야 한다.
@Component
class JpaShippingCommandAdapter(
    private val shippingRepository: ShippingRepository,
) : ShippingCommandPort {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun recordOutboundDelivered(rentalId: Long?, carrier: String, trackingNumber: String, at: LocalDateTime) {
        shippingRepository.save(
            Shipping(
                // ShippingCommandPort가 rentalId를 nullable로 받는 건 이 어댑터가 아직 자바이던
                // 시절의 흔적(boxed Long 시그니처 호환)이다 — 실제로 호출부가 null을 넘기는 일은
                // 없으므로 여기서 바로 확정한다.
                rentalId = rentalId!!,
                type = ShippingType.OUTBOUND,
                carrier = carrier,
                trackingNumber = trackingNumber,
                status = ShippingStatus.DELIVERED,
                shippedAt = at,
                deliveredAt = at,
            ),
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun recordReturnDelivered(rentalId: Long?, at: LocalDateTime) {
        // 반송은 택배사·송장을 받지 않는다. 기존 동작 그대로다.
        shippingRepository.save(
            Shipping(
                rentalId = rentalId!!,
                type = ShippingType.RETURN,
                status = ShippingStatus.DELIVERED,
                shippedAt = at,
                deliveredAt = at,
            ),
        )
    }
}
