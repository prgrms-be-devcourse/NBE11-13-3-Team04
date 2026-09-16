package com.example.iter.device.util

import com.example.iter.auth.api.UserSummary
import com.example.iter.device.domain.entity.Equipment
import com.example.iter.device.domain.entity.EquipmentImage
import com.example.iter.device.dto.response.AdminEquipmentDetailResponse
import com.example.iter.device.dto.response.AdminEquipmentSummaryResponse
import com.example.iter.device.dto.response.ImageResponse
import org.springframework.stereotype.Component

@Component
class AdminEquipmentMapper {

    // 장비, 등록자, 썸네일을 관리자 장비 목록 응답으로 변환합니다.
    // id 단언은 전부 같은 근거다 — 저장된 엔티티만 여기로 들어온다.
    fun toSummary(
        equipment: Equipment,
        owner: UserSummary,
        thumbnailUrl: String?,
    ): AdminEquipmentSummaryResponse = AdminEquipmentSummaryResponse(
        equipment.id!!,
        equipment.name,
        equipment.category,
        equipment.dailyPrice,
        equipment.status,
        owner,
        thumbnailUrl,
        equipment.createdAt,
    )

    // 장비, 등록자, 전체 이미지 목록을 관리자 장비 상세 응답으로 변환합니다.
    fun toDetail(
        equipment: Equipment,
        owner: UserSummary,
        images: List<EquipmentImage>,
    ): AdminEquipmentDetailResponse = AdminEquipmentDetailResponse(
        equipment.id!!,
        owner,
        equipment.category,
        equipment.name,
        equipment.description,
        equipment.dailyPrice,
        equipment.availableFrom,
        equipment.availableTo,
        equipment.status,
        equipment.productCondition,
        equipment.conditionDetail,
        images.map(::toImageResponse),
        equipment.createdAt,
        equipment.updatedAt,
    )

    // 장비 이미지 엔티티를 이미지 응답 DTO로 변환합니다.
    private fun toImageResponse(image: EquipmentImage): ImageResponse = ImageResponse(
        image.id!!,
        image.imageUrl,
        image.captureView,
        image.sortOrder,
        image.thumbnail
    )
}
