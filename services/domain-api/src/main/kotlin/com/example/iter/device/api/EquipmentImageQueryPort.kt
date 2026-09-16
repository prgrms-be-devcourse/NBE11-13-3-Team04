package com.example.iter.device.api

// device가 다른 도메인에 공개하는 장비 이미지 조회 창구입니다.
interface EquipmentImageQueryPort {

    // 정렬 순서와 ID 순서가 보장된 장비 이미지 목록을 반환합니다.
    fun findAll(equipmentId: Long): List<EquipmentImageInfo>
}
