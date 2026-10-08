package iter.chat.service

import iter.chat.domain.Message
import iter.chat.dto.response.MessagePageResponse
import iter.chat.dto.response.MessageResponse
import iter.chat.repository.MessageRepository
import kotlinx.coroutines.flow.toList
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class ChatMessageService(
    private val messageRepository: MessageRepository,
) {

    // cursor가 없으면 최신부터, 있으면 그보다 오래된 메시지부터 size개를 가져온다.
    // id 내림차순이라 messages 리스트 자체가 이미 "최신 → 과거" 순서다.
    suspend fun getMessages(roomId: Long, cursor: Long?, size: Int): MessagePageResponse {
        val pageable = PageRequest.of(0, size)
        val messages = if (cursor == null) {
            messageRepository.findByRoomIdOrderByIdDesc(roomId, pageable)
        } else {
            messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, pageable)
        }.toList()

        val nextCursor = if (messages.size == size) messages.last().id else null
        return MessagePageResponse(
            messages = messages.map(::toResponse),
            nextCursor = nextCursor,
        )
    }

    private fun toResponse(message: Message) = MessageResponse(
        id = message.id!!,
        roomId = message.roomId,
        senderId = message.senderId,
        senderNickname = message.senderNickname,
        type = message.type,
        content = message.content,
        masked = message.masked,
        sentAt = message.sentAt,
    )
}
