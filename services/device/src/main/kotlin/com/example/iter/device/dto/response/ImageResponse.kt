package com.example.iter.device.dto.response

data class ImageResponse(
    val imageId: Long,
    val imageUrl: String,
    val sortOrder: Int,
    val thumbnail: Boolean,
)
