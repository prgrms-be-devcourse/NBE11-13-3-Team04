package iter.chatbridge.redis

// Redis chat:grant:{token} 에 JSON으로 저장되는 값. apps:chat이 GETDEL로 소비해서
// 방을 만든다 — chat은 device/auth DB를 못 보므로 필요한 정보를 여기 다 실어 보낸다.
// ChatTicketPayload와 같은 이유로 필드 이름이 곧 계약이다.
@JvmRecord
data class ChatInquiryGrantPayload(
    val equipmentId: Long,
    val equipmentName: String,
    val ownerId: Long,
    val ownerNickname: String?,
    val requesterId: Long,
    val requesterNickname: String?,
)
