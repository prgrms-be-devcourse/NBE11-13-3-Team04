package iter.event.contract

import java.time.Instant

// chat이 같은 유저의 위반이 최근 30일 내 5회 이상 쌓이면 발행한다(ViolationService).
// 계정 정지 여부는 chat이 결정하지 않는다 — 관리자 검토 큐로 사실만 알리는 용도다.
data class ChatPolicyViolatedEvent(
    val eventId: String,
    val roomId: Long,
    val userId: Long,
    val violationCount: Long,
    val occurredAt: Instant,
)
