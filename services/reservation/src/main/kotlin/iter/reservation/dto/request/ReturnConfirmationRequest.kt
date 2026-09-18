package iter.reservation.dto.request

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ReturnConfirmationRequest(
    @field:NotNull(message = "상품 이상 여부는 필수입니다.")
    val hasIssue: Boolean?,

    @field:Size(max = 50, message = "분쟁 사유는 50자 이하여야 합니다.")
    val disputeReason: String?,

    @field:Size(max = 2000, message = "분쟁 설명은 2000자 이하여야 합니다.")
    val disputeDescription: String?,
) {
    // ReturnService(자바)가 record 접근자 스타일로 그대로 부른다.
    fun hasIssue(): Boolean? = hasIssue
    fun disputeReason(): String? = disputeReason
    fun disputeDescription(): String? = disputeDescription

    @get:JsonIgnore
    @get:AssertTrue(message = "상품에 이상이 있으면 분쟁 사유와 설명이 필요합니다.")
    val isDisputeInputValid: Boolean
        get() {
            if (hasIssue == null) {
                return true
            }

            val hasReason = !disputeReason.isNullOrBlank()
            val hasDescription = !disputeDescription.isNullOrBlank()

            return if (hasIssue) {
                hasReason && hasDescription
            } else {
                !hasReason && !hasDescription
            }
        }
}
