package iter.chat.redis

// monolith(ChatTicketPayload/ChatInquiryGrantPayload, Java record)가 JSON으로 저장해
// 둔 값을 그대로 역직렬화한다. 필드 이름이 두 앱을 잇는 계약이다 — monolith 쪽을
// 바꾸면 여기도 같이 바꿔야 한다(클래스를 공유하지 않으므로 컴파일로는 안 잡힌다).
data class TicketPayload(val userId: Long, val nickname: String)

data class GrantPayload(
    val equipmentId: Long,
    val equipmentName: String,
    val ownerId: Long,
    val ownerNickname: String,
    val requesterId: Long,
    val requesterNickname: String,
)
