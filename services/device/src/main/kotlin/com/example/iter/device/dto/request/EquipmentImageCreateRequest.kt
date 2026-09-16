package com.example.iter.device.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class EquipmentImageCreateRequest(
    @field:NotEmpty(message = "추가할 장비 이미지는 한 장 이상 필요합니다.")
    @field:Size(max = 5, message = "장비 이미지는 한 번에 5장 이하로 추가해주세요.")
    val imageKeys: List<@NotBlank(message = "이미지 객체 키는 빈 값일 수 없습니다.") String>?,

    val thumbnailIndex: Int?,
) {
    @get:AssertTrue(message = "대표 이미지 인덱스가 추가 이미지 목록 범위를 벗어났습니다.")
    val isValidThumbnailIndex: Boolean
        get() = thumbnailIndex == null ||
            imageKeys == null ||
            thumbnailIndex >= 0 && thumbnailIndex < imageKeys.size

    @AssertTrue(message = "중복된 이미지 객체 키는 등록할 수 없습니다.")
    fun hasNoDuplicateImageKeys(): Boolean =
        imageKeys == null || imageKeys.toSet().size == imageKeys.size
}
