package com.example.iter.chat.repository

import com.example.iter.chat.domain.RoomParticipant
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface RoomParticipantRepository : CoroutineCrudRepository<RoomParticipant, Long> {

    fun findByRoomId(roomId: Long): Flow<RoomParticipant>

    // WebSocket 연결 시 (roomId, userId)가 이 방의 참여자인지 확인하는 용도.
    suspend fun findByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant?
}
