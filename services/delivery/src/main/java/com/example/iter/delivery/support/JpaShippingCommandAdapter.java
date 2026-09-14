package com.example.iter.delivery.support;

import com.example.iter.delivery.api.ShippingCommandPort;
import com.example.iter.delivery.domain.entity.Shipping;
import com.example.iter.delivery.domain.entity.ShippingStatus;
import com.example.iter.delivery.domain.entity.ShippingType;
import com.example.iter.delivery.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// delivery/api/ShippingCommandPort 의 모놀리스 구현.
// 엔티티 조립이 여기로 들어왔다 — 호출부는 이제 ShippingType/ShippingStatus 를 모른다.
//
// !! propagation = MANDATORY 다 !!
// 배송 기록과 대여 상태 변경이 함께 커밋되거나 함께 롤백돼야 한다.
@Component
@RequiredArgsConstructor
public class JpaShippingCommandAdapter implements ShippingCommandPort {

    private final ShippingRepository shippingRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOutboundDelivered(Long rentalId, String carrier, String trackingNumber, LocalDateTime at) {
        shippingRepository.save(Shipping.builder()
                .rentalId(rentalId)
                .type(ShippingType.OUTBOUND)
                .carrier(carrier)
                .trackingNumber(trackingNumber)
                .status(ShippingStatus.DELIVERED)
                .shippedAt(at)
                .deliveredAt(at)
                .build());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReturnDelivered(Long rentalId, LocalDateTime at) {
        // 반송은 택배사·송장을 받지 않는다. 기존 동작 그대로다.
        shippingRepository.save(Shipping.builder()
                .rentalId(rentalId)
                .type(ShippingType.RETURN)
                .status(ShippingStatus.DELIVERED)
                .shippedAt(at)
                .deliveredAt(at)
                .build());
    }
}
