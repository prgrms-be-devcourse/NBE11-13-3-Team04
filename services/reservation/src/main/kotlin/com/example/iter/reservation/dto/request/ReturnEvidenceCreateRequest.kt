package com.example.iter.reservation.dto.request

import com.example.iter.reservation.domain.entity.ProductConditionType
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class ReturnEvidenceCreateRequest(
    @field:NotNull(message = "반납 시점 상품 상태는 필수입니다.")
    val productCondition: ProductConditionType?,

    val conditionDetail: String?,

    @field:NotEmpty(message = "반납 증빙 사진은 최소 1장 필요합니다.")
    val imageUrls: List<@NotEmpty String>?,
) {
    fun productCondition(): ProductConditionType? = productCondition
    fun conditionDetail(): String? = conditionDetail
    fun imageUrls(): List<String>? = imageUrls
}
