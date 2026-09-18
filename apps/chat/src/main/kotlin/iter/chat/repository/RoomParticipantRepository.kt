package iter.chat.repository

import iter.chat.domain.RoomParticipant
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface RoomParticipantRepository : CoroutineCrudRepository<RoomParticipant, Long> {

    fun findByRoomId(roomId: Long): Flow<RoomParticipant>

    // 방 목록(GET /rooms) — 내가 참여한 모든 방을 찾는 시작점.
    fun findByUserId(userId: Long): Flow<RoomParticipant>

    // WebSocket 연결 시 (roomId, userId)가 이 방의 참여자인지 확인하는 용도.
    suspend fun findByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant?
}
