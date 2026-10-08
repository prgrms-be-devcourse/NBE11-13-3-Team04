package iter.event.contract

import java.time.Instant

// monolith가 결제 확정(PaymentConfirmedEvent, services/domain-api) 커밋 후 이 이벤트로
// 살을 붙여 Redis Stream(iter.events.chat)에 발행한다. chat은 이 정보로 문의방을 찾아
// stage를 INQUIRY → TRADE로 전환한다(문의 없이 바로 결제했으면 방을 새로 만든다).
//
// monolith(자바)와 chat(코틀린) 둘 다 이 모듈을 직접 의존해서 같은 클래스를 쓴다 —
// 티켓/그랜트 JSON(필드 이름만 계약)과 다르게, 여기는 두 앱이 계속 같이 발전시킬
// 정식 이벤트 스키마라 컴파일 타임에 맞춰 두는 쪽을 택했다.
data class PaymentConfirmedIntegrationEvent(
    val eventId: String,
    val rentalId: Long,
    val equipmentId: Long,
    val renterId: Long,
    val ownerId: Long,
    val occurredAt: Instant,
)
