package com.example.iter.device.dto.response

import com.example.iter.device.domain.entity.EquipmentImage

data class EquipmentImageResponse(
    val id: Long?,
    val imageUrl: String,
    val sortOrder: Int,
    val thumbnail: Boolean,
) {
    companion object {
        // 자바에서 EquipmentImageResponse.from(...) 으로 부르므로 @JvmStatic 이 필요하다.
        @JvmStatic
        fun from(image: EquipmentImage, resolvedImageUrl: String) = EquipmentImageResponse(
            id = image.id,
            imageUrl = resolvedImageUrl,
            sortOrder = image.sortOrder,
            thumbnail = image.thumbnail,
        )
    }
}
