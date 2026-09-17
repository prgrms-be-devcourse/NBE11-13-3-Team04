package com.example.iter.chat.repository

import com.example.iter.chat.domain.Message
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface MessageRepository : CoroutineCrudRepository<Message, Long> {

    // 과거 메시지 커서 페이지(REST GET /rooms/{roomId}/messages) 용.
    // id 내림차순 = idx_room_id_id(room_id, id DESC)를 그대로 탄다.
    fun findByRoomIdAndIdLessThanOrderByIdDesc(roomId: Long, id: Long, pageable: Pageable): Flow<Message>

    fun findByRoomIdOrderByIdDesc(roomId: Long, pageable: Pageable): Flow<Message>

    // 방 목록의 "마지막 메시지"용. 정렬은 findByRoomIdOrderByIdDesc와 같은 인덱스를 탄다.
    suspend fun findFirstByRoomIdOrderByIdDesc(roomId: Long): Message?

    // 미읽음 수 계산 — last_read_message_id가 있으면 그 이후 것만, 없으면 전체를 센다
    // (참여자 서비스에서 null 여부에 따라 둘 중 하나를 호출한다).
    suspend fun countByRoomIdAndIdGreaterThan(roomId: Long, id: Long): Long

    suspend fun countByRoomId(roomId: Long): Long
}
