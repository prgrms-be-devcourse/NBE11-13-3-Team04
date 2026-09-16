package com.example.iter.device.dto.request

import com.example.iter.common.dto.request.CapturedImageRequest
import com.example.iter.common.image.CaptureView
import com.example.iter.device.domain.entity.EquipmentCategory
import com.example.iter.device.domain.entity.ProductConditionType
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

import java.math.BigDecimal
import java.time.LocalDate
import java.util.EnumSet

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

    @field:NotEmpty(message = "장비 이미지는 필수입니다.")
    @field:Size(min = 3, max = 3, message = "정면·측면·후면 사진을 각각 한 장씩 등록해주세요.")
    @field:Valid
    val images: List<CapturedImageRequest>?
) {
    @get:AssertTrue(message = "대여 가능 종료일은 시작일보다 빠를 수 없습니다.")
    val isValidAvailablePeriod: Boolean
        get() = availableFrom == null || availableTo == null || !availableTo.isBefore(availableFrom)

    @get:AssertTrue(message = "NORMAL이 아닌 장비는 컨디션 상세 설명이 필수입니다.")
    val isValidConditionDetail: Boolean
        get() = productCondition == null ||
            productCondition == ProductConditionType.NORMAL ||
            conditionDetail != null && conditionDetail.isNotBlank()

    @get:AssertTrue(message = "정면·측면·후면 사진을 각각 한 장씩 등록해주세요.")
    val hasAllCaptureViews: Boolean
        get() {
            if (images == null || images.size != CaptureView.entries.size) {
                return false
            }

            val views = images.mapNotNull(CapturedImageRequest::captureView).toSet()

            return views == EnumSet.allOf(CaptureView::class.java)
        }

    @AssertTrue(message = "중복된 이미지 객체 키는 등록할 수 없습니다.")
    fun hasNoDuplicateImageKeys(): Boolean =
        images == null || images.map(CapturedImageRequest::objectKey).toSet().size == images.size

    fun orderedImages(): List<CapturedImageRequest> = images
        ?.sortedWith(compareBy(nullsLast()) { it.captureView })
        ?: emptyList()

    val imageKeys: List<String>
        get() = orderedImages().map { requireNotNull(it.objectKey) }
}
