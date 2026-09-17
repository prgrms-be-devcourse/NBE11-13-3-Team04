package com.example.iter.chat.security

// TicketAuthWebFilter가 티켓을 검증한 뒤 요청에 붙여 두는 값. monolith의
// CustomUserDetails에 대응하지만, chat은 JWT/DB를 안 보므로 티켓에 실려온
// userId/nickname 그대로다(재조회하지 않는다).
data class ChatPrincipal(val userId: Long, val nickname: String)
