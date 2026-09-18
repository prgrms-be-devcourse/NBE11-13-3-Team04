package com.example.iter.event.contract

import java.time.Instant

// monolith가 거래 최종 완료(RentalCompletedEvent, services/domain-api) 커밋 후 이 이벤트로
// 살을 붙여 Redis Stream(iter.events.chat)에 발행한다. chat은 이 정보로 거래방을 찾아
// stage를 TRADE → INQUIRY로 되돌린다.
//
// 되돌리는 이유: 방은 (equipmentId, requesterId)로 재사용되는데(ChatRoomService.createRoom),
// stage가 TRADE에 머물면 그 조합은 이후 새 문의를 해도 정책 필터가 영영 꺼진 채로 남는다.
//
// chat이 방을 찾는 키가 (equipmentId, renterId)라서 그 둘이 필수다. rentalId는 멱등 판정에
// 쓴다 — 재전달된 과거 이벤트가 이미 시작된 새 거래를 되돌리면 안 된다.
data class RentalCompletedIntegrationEvent(
    val eventId: String,
    val rentalId: Long,
    val equipmentId: Long,
    val renterId: Long,
    val occurredAt: Instant,
)
