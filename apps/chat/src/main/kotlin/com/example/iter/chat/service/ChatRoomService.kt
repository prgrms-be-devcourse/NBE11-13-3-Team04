package com.example.iter.chat.service

import com.example.iter.chat.domain.ChatRoom
import com.example.iter.chat.domain.RoomParticipant
import com.example.iter.chat.dto.response.RoomSummaryResponse
import com.example.iter.chat.exception.ChatErrorCode
import com.example.iter.chat.exception.ChatException
import com.example.iter.chat.redis.GrantStore
import com.example.iter.chat.repository.ChatRoomRepository
import com.example.iter.chat.repository.MessageRepository
import com.example.iter.chat.repository.RoomParticipantRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class ChatRoomService(
    private val grantStore: GrantStore,
    private val chatRoomRepository: ChatRoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
    private val messageRepository: MessageRepository,
) {

    // grantToken은 1회성이라(GrantStore.consume이 GETDEL) 같은 토큰으로 두 번 호출하면
    // 두 번째는 무조건 GRANT_INVALID_OR_EXPIRED다. 방 자체의 재사용은 grant 없이
    // (equipmentId, requesterId)로 이미 있는 방을 다시 찾는 것으로 이뤄진다 — 그 경우
    // 프론트는 새 grant를 발급받지 않고 그냥 GET /rooms로 기존 방을 봐야 한다.
    suspend fun createRoom(requesterId: Long, grantToken: String): ChatRoom {
        val grant = grantStore.consume(grantToken) ?: throw ChatException(ChatErrorCode.GRANT_INVALID_OR_EXPIRED)
        if (grant.requesterId != requesterId) {
            throw ChatException(ChatErrorCode.GRANT_USER_MISMATCH)
        }

        val existing = chatRoomRepository.findByEquipmentIdAndRequesterId(grant.equipmentId, grant.requesterId)
        if (existing != null) {
            return existing
        }

        val room = chatRoomRepository.save(
            ChatRoom(
                equipmentId = grant.equipmentId,
                equipmentName = grant.equipmentName,
                ownerId = grant.ownerId,
                requesterId = grant.requesterId,
            ),
        )
        // 새로 만든 방이라 참여자가 있을 수 없다 — upsert 없이 그냥 두 건 추가한다.
        roomParticipantRepository.save(RoomParticipant(roomId = room.id!!, userId = grant.ownerId, nickname = grant.ownerNickname))
        roomParticipantRepository.save(
            RoomParticipant(roomId = room.id, userId = grant.requesterId, nickname = grant.requesterNickname),
        )
        return room
    }

    suspend fun listRooms(userId: Long): List<RoomSummaryResponse> =
        roomParticipantRepository.findByUserId(userId)
            .map { participant -> toSummary(userId, participant) }
            .toList()

    suspend fun markRead(userId: Long, roomId: Long, lastReadMessageId: Long) {
        val participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
            ?: throw ChatException(ChatErrorCode.ROOM_ACCESS_DENIED)
        roomParticipantRepository.save(participant.copy(lastReadMessageId = lastReadMessageId))
    }

    suspend fun requireRoom(roomId: Long): ChatRoom =
        chatRoomRepository.findById(roomId) ?: throw ChatException(ChatErrorCode.ROOM_NOT_FOUND)

    // WebSocket 연결(CH5)에서도 그대로 쓸 수 있게 public으로 둔다.
    suspend fun requireParticipant(roomId: Long, userId: Long): RoomParticipant =
        roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
            ?: throw ChatException(ChatErrorCode.ROOM_ACCESS_DENIED)

    private suspend fun toSummary(userId: Long, participant: RoomParticipant): RoomSummaryResponse {
        val room = requireRoom(participant.roomId)
        val counterpartId = if (room.ownerId == userId) room.requesterId else room.ownerId
        val counterpart = roomParticipantRepository.findByRoomIdAndUserId(room.id!!, counterpartId)

        val lastMessage = messageRepository.findFirstByRoomIdOrderByIdDesc(room.id)
        val unreadCount = when (val lastReadId = participant.lastReadMessageId) {
            null -> messageRepository.countByRoomId(room.id)
            else -> messageRepository.countByRoomIdAndIdGreaterThan(room.id, lastReadId)
        }

        return RoomSummaryResponse(
            roomId = room.id,
            equipmentId = room.equipmentId,
            equipmentName = room.equipmentName,
            stage = room.stage,
            counterpartId = counterpartId,
            counterpartNickname = counterpart?.nickname ?: "",
            lastMessage = lastMessage?.content,
            lastMessageAt = lastMessage?.sentAt,
            unreadCount = unreadCount,
        )
    }
}
