package iter.reservation.util

import iter.auth.api.UserSummary
import iter.reservation.domain.entity.Receipt
import iter.reservation.domain.entity.Rental
import iter.reservation.domain.entity.ReturnReceipt
import iter.reservation.dto.response.ConditionEvidenceImageResponse
import iter.reservation.dto.response.ConditionEvidenceResponse
import iter.reservation.dto.response.ReturnComparisonResponse
import iter.reservation.dto.response.ReturnConfirmationResponse
import iter.reservation.dto.response.ReturnTargetResponse
import org.springframework.stereotype.Component

@Component
class ReturnMapper {

    // 반납 확인 대상 목록 응답으로 변환합니다.
    fun toTarget(
        rental: Rental,
        renter: UserSummary,
        thumbnailUrl: String?,
        returnReceipt: ReturnReceipt,
    ): ReturnTargetResponse =
        ReturnTargetResponse(
            rental.id,
            rental.productNameSnapshot,
            thumbnailUrl,
            renter,
            rental.endDate,
            returnReceipt.returnDate,
        )

    // 수령·반납 증빙 비교 응답으로 변환합니다.
    fun toComparison(
        rental: Rental,
        renter: UserSummary,
        listingImages: List<ConditionEvidenceImageResponse>,
        receipt: Receipt,
        receiptImages: List<ConditionEvidenceImageResponse>,
        returnReceipt: ReturnReceipt,
        returnImages: List<ConditionEvidenceImageResponse>,
    ): ReturnComparisonResponse =
        ReturnComparisonResponse(
            rental.id,
            rental.productNameSnapshot,
            renter,
            rental.startDate,
            rental.endDate,
            returnReceipt.returnDate,
            listingImages.toList(),
            toEvidence(receipt, receiptImages),
            toEvidence(returnReceipt, returnImages),
        )

    // 반납 최종 확인 응답으로 변환합니다.
    fun toConfirmation(rental: Rental, disputeId: Long?, reportId: Long?): ReturnConfirmationResponse =
        ReturnConfirmationResponse(rental.id, rental.status, disputeId, reportId)

    // ============================================================

    private fun toEvidence(receipt: Receipt, images: List<ConditionEvidenceImageResponse>): ConditionEvidenceResponse =
        ConditionEvidenceResponse(receipt.productCondition, receipt.conditionDetail, images.toList(), receipt.receivedAt)

    private fun toEvidence(
        returnReceipt: ReturnReceipt,
        images: List<ConditionEvidenceImageResponse>,
    ): ConditionEvidenceResponse =
        ConditionEvidenceResponse(
            returnReceipt.productCondition,
            returnReceipt.conditionDetail,
            images.toList(),
            returnReceipt.createdAt,
        )
}
