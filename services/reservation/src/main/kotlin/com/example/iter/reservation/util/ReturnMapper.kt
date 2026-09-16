package com.example.iter.reservation.util

import com.example.iter.auth.api.UserSummary
import com.example.iter.reservation.domain.entity.Receipt
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.entity.ReturnReceipt
import com.example.iter.reservation.dto.response.ConditionEvidenceResponse
import com.example.iter.reservation.dto.response.ReturnComparisonResponse
import com.example.iter.reservation.dto.response.ReturnConfirmationResponse
import com.example.iter.reservation.dto.response.ReturnTargetResponse
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
        receipt: Receipt,
        receiptImageUrls: List<String>,
        returnReceipt: ReturnReceipt,
        returnImageUrls: List<String>,
    ): ReturnComparisonResponse =
        ReturnComparisonResponse(
            rental.id,
            rental.productNameSnapshot,
            renter,
            rental.startDate,
            rental.endDate,
            returnReceipt.returnDate,
            toEvidence(receipt, receiptImageUrls),
            toEvidence(returnReceipt, returnImageUrls),
        )

    // 반납 최종 확인 응답으로 변환합니다.
    fun toConfirmation(rental: Rental, disputeId: Long?): ReturnConfirmationResponse =
        ReturnConfirmationResponse(rental.id, rental.status, disputeId)

    // ============================================================

    private fun toEvidence(receipt: Receipt, imageUrls: List<String>): ConditionEvidenceResponse =
        ConditionEvidenceResponse(receipt.productCondition, receipt.conditionDetail, imageUrls, receipt.receivedAt)

    private fun toEvidence(returnReceipt: ReturnReceipt, imageUrls: List<String>): ConditionEvidenceResponse =
        ConditionEvidenceResponse(
            returnReceipt.productCondition,
            returnReceipt.conditionDetail,
            imageUrls,
            returnReceipt.createdAt,
        )
}
