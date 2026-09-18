package iter.reservation.dto.response

import iter.reservation.domain.entity.ProductConditionType
import java.time.LocalDateTime

class ConditionEvidenceResponse(
    val productCondition: ProductConditionType?,
    val conditionDetail: String?,
    images: List<ConditionEvidenceImageResponse>?,
    val recordedAt: LocalDateTime?,
) {
    val images: List<ConditionEvidenceImageResponse> = java.util.List.copyOf(images ?: emptyList())

    fun productCondition(): ProductConditionType? = productCondition
    fun conditionDetail(): String? = conditionDetail
    fun images(): List<ConditionEvidenceImageResponse> = images
    fun recordedAt(): LocalDateTime? = recordedAt

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ConditionEvidenceResponse &&
            productCondition == other.productCondition &&
            conditionDetail == other.conditionDetail &&
            images == other.images &&
            recordedAt == other.recordedAt

    override fun hashCode(): Int {
        var result = productCondition?.hashCode() ?: 0
        result = 31 * result + (conditionDetail?.hashCode() ?: 0)
        result = 31 * result + images.hashCode()
        result = 31 * result + (recordedAt?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "ConditionEvidenceResponse(" +
            "productCondition=$productCondition, " +
            "conditionDetail=$conditionDetail, " +
            "images=$images, " +
            "recordedAt=$recordedAt)"
}
