package com.example.iter.chat.dto.response

import com.example.iter.chat.domain.MessageType
import java.time.Instant

data class MessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long?,
    val senderNickname: String?,
    val type: MessageType,
    val content: String,
    val masked: Boolean,
    val sentAt: Instant,
)

// id 내림차순으로 온 메시지 중 가장 오래된 것의 id를 nextCursor로 준다 — 다음 페이지
// 요청에 그대로 넘기면 그보다 더 오래된 메시지를 받는다. 더 없으면 null.
data class MessagePageResponse(
    val messages: List<MessageResponse>,
    val nextCursor: Long?,
)
