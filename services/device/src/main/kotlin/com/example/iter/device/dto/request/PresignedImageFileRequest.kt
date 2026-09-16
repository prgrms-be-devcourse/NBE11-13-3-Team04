package com.example.iter.device.dto.request

import com.example.iter.common.image.CaptureView
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class PresignedImageFileRequest(
    @field:NotNull(message = "사진 촬영 방향은 필수입니다.")
    val captureView: CaptureView?,

    @field:NotBlank(message = "파일명은 필수입니다.")
    val fileName: String,

    @field:NotBlank(message = "이미지 Content-Type은 필수입니다.")
    val contentType: String,

    @field:Positive(message = "이미지 크기는 0보다 커야 합니다.")
    val size: Long
)
