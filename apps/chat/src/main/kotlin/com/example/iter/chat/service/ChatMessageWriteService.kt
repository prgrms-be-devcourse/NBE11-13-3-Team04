package com.example.iter.chat.service

import com.example.iter.chat.domain.Message
import com.example.iter.chat.domain.MessageType
import com.example.iter.chat.domain.RoomStage
import com.example.iter.chat.policy.ContentPolicy
import com.example.iter.chat.policy.MaskingResult
import com.example.iter.chat.policy.ViolationCategory
import com.example.iter.chat.repository.ChatRoomRepository
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.repository.RoomParticipantRepository
import com.example.iter.chat.security.ChatPrincipal
import java.time.Instant
import org.springframework.stereotype.Service

sealed class SendResult {
    data class Sent(val message: Message, val violations: List<ViolationCategory>) : SendResult()

    // 3회 위반으로 24시간 전송 제한된 상태 — 저장하지 않는다(원문도 안 남긴다).
    data object Muted : SendResult()
}

// WebSocket 전송 경로 전용(REST는 조회만 한다 — ChatMessageService). 정책 검사는
// stage=INQUIRY일 때만 적용한다 — 결제 후(TRADE)는 배송 주소 등 개인정보를 주고받아야
// 해서 정책을 끈다.
@Service
class ChatMessageWriteService(
    private val messageRepository: MessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {
    suspend fun send(roomId: Long, sender: ChatPrincipal, content: String): SendResult {
        val participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, sender.userId)
        if (participant?.mutedUntil?.isAfter(Instant.now()) == true) {
            return SendResult.Muted
        }

        val room = chatRoomRepository.findById(roomId)
        val policyResult = if (room?.stage == RoomStage.INQUIRY) {
            ContentPolicy.apply(content)
        } else {
            MaskingResult.clean(content)
        }

        val saved = messageRepository.save(
            Message(
                roomId = roomId,
                senderId = sender.userId,
                senderNickname = sender.nickname,
                type = MessageType.USER,
                content = policyResult.maskedContent,
                masked = policyResult.masked,
            ),
        )
        return SendResult.Sent(saved, policyResult.violations)
    }
}
