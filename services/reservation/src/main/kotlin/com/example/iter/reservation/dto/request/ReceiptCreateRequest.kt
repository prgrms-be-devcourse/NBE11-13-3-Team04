package com.example.iter.reservation.dto.request

import com.example.iter.reservation.domain.entity.ProductConditionType
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class ReceiptCreateRequest(
    @field:NotNull(message = "수령 시점 상품 상태는 필수입니다.")
    val productCondition: ProductConditionType?,

    val conditionDetail: String?,

    @field:NotEmpty(message = "수령 증빙 사진은 최소 1장 필요합니다.")
    val imageUrls: List<@NotEmpty String>?,
) {
    // RentalFulfillmentService(자바)가 record 접근자 스타일(request.productCondition() 등)로
    // 그대로 부른다 — EvidenceImagePresignRequest와 동일한 이유로 브리지 함수를 둔다.
    fun productCondition(): ProductConditionType? = productCondition
    fun conditionDetail(): String? = conditionDetail
    fun imageUrls(): List<String>? = imageUrls
}
