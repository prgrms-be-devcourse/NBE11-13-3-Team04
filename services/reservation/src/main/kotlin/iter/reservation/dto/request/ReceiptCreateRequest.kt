package iter.reservation.dto.request

import iter.common.dto.request.CapturedImageRequest
import iter.reservation.domain.entity.ProductConditionType
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ReceiptCreateRequest(
    @field:NotNull(message = "수령 시점 상품 상태는 필수입니다.")
    val productCondition: ProductConditionType?,

    val conditionDetail: String?,

    @field:Size(min = 3, max = 3, message = "수령 사진은 정면·측면·후면 각각 한 장씩 필요합니다.")
    @field:Valid
    val images: List<CapturedImageRequest>?,
) {
    fun productCondition(): ProductConditionType? = productCondition
    fun conditionDetail(): String? = conditionDetail
    fun images(): List<CapturedImageRequest>? = images

    @get:AssertTrue(message = "수령 사진은 정면·측면·후면 각각 한 장씩 필요합니다.")
    val hasAllCaptureViews: Boolean
        get() = CaptureImageRequestRules.hasAllViews(images)

    @get:AssertTrue(message = "중복된 수령 사진은 제출할 수 없습니다.")
    val hasNoDuplicateKeys: Boolean
        get() = CaptureImageRequestRules.hasNoDuplicateKeys(images)
}
