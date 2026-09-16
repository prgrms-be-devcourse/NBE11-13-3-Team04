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
}
