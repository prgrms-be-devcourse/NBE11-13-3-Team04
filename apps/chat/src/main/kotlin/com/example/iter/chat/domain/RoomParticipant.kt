package com.example.iter.chat.domain

import org.springframework.data.annotation.Id
import java.time.Instant
import org.springframework.data.relational.core.mapping.Table

// 관계상 자연키는 (room_id, user_id)지만 Spring Data R2DBC가 복합 @Id를 지원하지
// 않아서 대리키(id)를 둔다. 중복 방지는 DB의 UNIQUE(room_id, user_id) 제약이 한다.
//
// nickname은 방 생성 시 grant가 실어 온 값을 그대로 복사해 둔다 — 메시지 전송 경로에서
// User 조회를 하지 않기 위한 의도적 비정규화다.
@Table("room_participants")
data class RoomParticipant(
    @Id
    val id: Long? = null,
    val roomId: Long,
    val userId: Long,
    val nickname: String,
    val lastReadMessageId: Long? = null,
    val joinedAt: Instant = Instant.now(),
    val mutedUntil: Instant? = null,
)
