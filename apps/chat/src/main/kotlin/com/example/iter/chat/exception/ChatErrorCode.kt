package com.example.iter.chat.exception

import org.springframework.http.HttpStatus

// monolith의 ErrorCode/CustomException과 같은 역할이지만, chat은 libs:core를 의존하지
// 않으므로(별도 앱) 여기서 다시 정의한다. 항목이 늘어나도 이 파일 하나에서 관리한다.
enum class ChatErrorCode(val status: HttpStatus, val message: String) {
    TICKET_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 티켓입니다."),
    GRANT_INVALID_OR_EXPIRED(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 문의 승인입니다."),
    GRANT_USER_MISMATCH(HttpStatus.FORBIDDEN, "본인이 발급받은 문의 승인이 아닙니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 채팅방에 접근할 권한이 없습니다."),
}
