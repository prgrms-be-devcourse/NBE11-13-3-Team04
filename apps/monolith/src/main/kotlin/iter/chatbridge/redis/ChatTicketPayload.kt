package iter.chatbridge.redis

// Redis chat:ticket:{token} 에 JSON으로 저장되는 값. apps:chat(Kotlin)이 같은
// 필드 이름의 data class로 그대로 역직렬화한다 — 두 앱이 클래스를 공유하지 않는
// 대신 이 JSON 스키마가 계약이다. 필드 이름을 바꾸면 반대편도 같이 바꿔야 한다.
@JvmRecord
data class ChatTicketPayload(val userId: Long, val nickname: String?)
