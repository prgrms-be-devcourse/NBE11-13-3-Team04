package com.example.iter.delivery.api

import java.time.LocalDateTime

// delivery 가 다른 도메인에게 공개하는 배송 기록 창구.
//
// 이전에는 reservation 이 Shipping 엔티티를 빌더로 조립하면서
// ShippingType 과 ShippingStatus 열거형까지 함께 끌고 갔다.
// 메서드 이름에 방향(발송/반송)을 담으면 그 열거형이 필요 없다.
interface ShippingCommandPort {

    // rentalId 는 nullable 로 선언한다 — 구현체(JpaShippingCommandAdapter)가
    // 아직 자바라 Long 파라미터가 boxed 참조형이다. 코틀린 non-null Long 은
    // 바이트코드에서 primitive long 으로 컴파일돼 자바 구현체의 override 시그니처가 깨진다.

    // 대여자에게 나가는 배송을 배달 완료로 기록한다.
    fun recordOutboundDelivered(rentalId: Long?, carrier: String, trackingNumber: String, at: LocalDateTime)

    // 등록자에게 돌아오는 반송을 배달 완료로 기록한다.
    fun recordReturnDelivered(rentalId: Long?, at: LocalDateTime)
}
