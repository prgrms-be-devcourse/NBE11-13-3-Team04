package com.example.iter.chat.repository

import com.example.iter.chat.domain.ChatRoom
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ChatRoomRepository : CoroutineCrudRepository<ChatRoom, Long> {

    // 방 생성/재사용의 멱등 키(uk_equipment_requester). 같은 사람이 같은 장비에
    // 다시 문의하면 이 방을 그대로 쓴다. PaymentConfirmedIntegrationEvent(CH7) 처리 시
    // stage 전환 대상 방을 찾는 데도 그대로 쓴다 — (equipmentId, renterId) 조합이 같다.
    suspend fun findByEquipmentIdAndRequesterId(equipmentId: Long, requesterId: Long): ChatRoom?
}
