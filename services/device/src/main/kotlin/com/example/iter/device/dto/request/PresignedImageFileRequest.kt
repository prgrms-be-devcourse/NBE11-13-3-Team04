package com.example.iter.device.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class PresignedImageFileRequest(
    @field:NotBlank(message = "파일명은 필수입니다.")
    val fileName: String,

    @field:NotBlank(message = "이미지 Content-Type은 필수입니다.")
    val contentType: String,

    @field:Positive(message = "이미지 크기는 0보다 커야 합니다.")
    val size: Long,
)
