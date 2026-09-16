package com.example.iter.chat.service

import com.example.iter.chat.domain.Message
import com.example.iter.chat.domain.MessageType
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.security.ChatPrincipal
import org.springframework.stereotype.Service

// WebSocket 전송 경로 전용(REST는 조회만 한다 — ChatMessageService). CH6에서 정책 엔진이
// 이 클래스 앞단에 끼어든다: 검사·마스킹을 마친 content가 여기로 들어오게 바뀔 것이다.
// 지금은(CH5) 원문 그대로 저장한다 — masked는 항상 false.
@Service
class ChatMessageWriteService(
    private val messageRepository: MessageRepository,
) {
    suspend fun send(roomId: Long, sender: ChatPrincipal, content: String): Message =
        messageRepository.save(
            Message(
                roomId = roomId,
                senderId = sender.userId,
                senderNickname = sender.nickname,
                type = MessageType.USER,
                content = content,
                masked = false,
            ),
        )
}
