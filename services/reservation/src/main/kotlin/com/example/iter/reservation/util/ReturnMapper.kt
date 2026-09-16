package com.example.iter.reservation.util

import com.example.iter.auth.api.UserSummary
import com.example.iter.reservation.domain.entity.Receipt
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.entity.ReturnReceipt
import com.example.iter.reservation.dto.response.ConditionEvidenceImageResponse
import com.example.iter.reservation.dto.response.ConditionEvidenceResponse
import com.example.iter.reservation.dto.response.ReturnComparisonResponse
import com.example.iter.reservation.dto.response.ReturnConfirmationResponse
import com.example.iter.reservation.dto.response.ReturnTargetResponse
import org.springframework.stereotype.Component

@Component
class ReturnMapper {

    fun toTarget(
        rental: Rental,
        renter: UserSummary,
        thumbnailUrl: String?,
        returnReceipt: ReturnReceipt
    ): ReturnTargetResponse = ReturnTargetResponse(
        rental.id,
        rental.productNameSnapshot,
        thumbnailUrl,
        renter,
        rental.endDate,
        returnReceipt.returnDate
    )

    fun toComparison(
        rental: Rental,
        renter: UserSummary,
        listingImages: List<ConditionEvidenceImageResponse>,
        receipt: Receipt,
        receiptImages: List<ConditionEvidenceImageResponse>,
        returnReceipt: ReturnReceipt,
        returnImages: List<ConditionEvidenceImageResponse>
    ): ReturnComparisonResponse = ReturnComparisonResponse(
        rental.id,
        rental.productNameSnapshot,
        renter,
        rental.startDate,
        rental.endDate,
        returnReceipt.returnDate,
        listingImages.toList(),
        toEvidence(receipt, receiptImages),
        toEvidence(returnReceipt, returnImages)
    )

    fun toConfirmation(rental: Rental, disputeId: Long?, reportId: Long?): ReturnConfirmationResponse = ReturnConfirmationResponse(
        rental.id,
        rental.status,
        disputeId,
        reportId
    )

    private fun toEvidence(receipt: Receipt, images: List<ConditionEvidenceImageResponse>): ConditionEvidenceResponse = ConditionEvidenceResponse(
        receipt.productCondition,
        receipt.conditionDetail,
        images.toList(),
        receipt.receivedAt
    )

    private fun toEvidence(
        returnReceipt: ReturnReceipt,
        images: List<ConditionEvidenceImageResponse>
    ): ConditionEvidenceResponse = ConditionEvidenceResponse(
        returnReceipt.productCondition,
        returnReceipt.conditionDetail,
        images.toList(),
        returnReceipt.createdAt
    )
}
