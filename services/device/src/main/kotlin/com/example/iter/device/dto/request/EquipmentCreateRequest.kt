package com.example.iter.device.dto.request

import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.ProductConditionType
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

import java.math.BigDecimal
import java.time.LocalDate

data class EquipmentCreateRequest(
    @field:NotNull(message = "장비 카테고리는 필수입니다.")
    val category: EquipmentCategory?,

    @field:NotBlank(message = "장비명은 필수입니다.")
    @field:Size(max = 100, message = "장비명은 100자 이하여야 합니다.")
    val name: String?,

    @field:NotBlank(message = "장비 설명은 필수입니다.")
    val description: String?,

    @field:NotNull(message = "1일 대여 가격은 필수입니다.")
    @field:Positive(message = "1일 대여 가격은 0보다 커야 합니다.")
    val dailyPrice: BigDecimal?,

    @field:NotNull(message = "대여 가능 시작일은 필수입니다.")
    @field:FutureOrPresent(message = "대여 가능 시작일은 현재 날짜 이상이어야 합니다.")
    val availableFrom: LocalDate?,

    @field:NotNull(message = "대여 가능 종료일은 필수입니다.")
    val availableTo: LocalDate?,

    @field:NotNull(message = "장비 컨디션은 필수입니다.")
    val productCondition: ProductConditionType?,

    val conditionDetail: String?,

    @field:NotEmpty(message = "장비 이미지는 한 장 이상 필요합니다.")
    @field:Size(max = 5, message = "장비 이미지는 5장 이하로 등록해주세요.")
    val imageKeys: List<@NotBlank(message = "이미지 객체 키는 빈 값일 수 없습니다.") String>?,

    @field:Min(value = 0, message = "대표 이미지 인덱스는 0 이상이어야 합니다.")
    val thumbnailIndex: Int = 0,
) {
    @get:AssertTrue(message = "대여 가능 종료일은 시작일보다 빠를 수 없습니다.")
    val isValidAvailablePeriod: Boolean
        get() = availableFrom == null || availableTo == null || !availableTo.isBefore(availableFrom)

    @get:AssertTrue(message = "NORMAL이 아닌 장비는 컨디션 상세 설명이 필수입니다.")
    val isValidConditionDetail: Boolean
        get() = productCondition == null ||
            productCondition == ProductConditionType.NORMAL ||
            conditionDetail != null && conditionDetail.isNotBlank()

    @get:AssertTrue(message = "대표 이미지 인덱스가 이미지 목록 범위를 벗어났습니다.")
    val isValidThumbnailIndex: Boolean
        get() = imageKeys == null || imageKeys.isEmpty() || thumbnailIndex < imageKeys.size

    @AssertTrue(message = "중복된 이미지 객체 키는 등록할 수 없습니다.")
    fun hasNoDuplicateImageKeys(): Boolean =
        imageKeys == null || imageKeys.toSet().size == imageKeys.size
}
