package com.example.iter.delivery.api;

import java.time.LocalDateTime;

// delivery 가 다른 도메인에게 공개하는 배송 기록 창구.
//
// 이전에는 reservation 이 Shipping 엔티티를 빌더로 조립하면서
// ShippingType 과 ShippingStatus 열거형까지 함께 끌고 갔다.
// 메서드 이름에 방향(발송/반송)을 담으면 그 열거형이 필요 없다.
public interface ShippingCommandPort {

    // 대여자에게 나가는 배송을 배달 완료로 기록한다.
    void recordOutboundDelivered(Long rentalId, String carrier, String trackingNumber, LocalDateTime at);

    // 등록자에게 돌아오는 반송을 배달 완료로 기록한다.
    void recordReturnDelivered(Long rentalId, LocalDateTime at);
}
