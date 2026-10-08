package iter.composition.ai

import iter.ai.service.PreviousConditionAnalysisService
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.delivery.domain.entity.Shipping
import iter.delivery.domain.entity.ShippingType
import iter.delivery.domain.repository.ShippingRepository
import iter.device.domain.repository.EquipmentRepository
import iter.dispute.domain.entity.Dispute
import iter.dispute.domain.entity.Report
import iter.dispute.domain.entity.ReportTargetType
import iter.dispute.domain.repository.DisputeRepository
import iter.payment.domain.entity.Payment
import iter.payment.domain.repository.PaymentRepository
import iter.reservation.domain.entity.Receipt
import iter.reservation.domain.entity.Rental
import iter.reservation.domain.entity.ReturnReceipt
import iter.reservation.domain.repository.ReceiptRepository
import iter.reservation.domain.repository.RentalRepository
import iter.reservation.domain.repository.ReturnReceiptRepository
import org.springframework.stereotype.Component

@Component
class RentalReportContextContributor(
    private val rentals: RentalRepository,
    private val equipment: EquipmentRepository,
    private val payments: PaymentRepository,
    private val shipping: ShippingRepository,
    private val receipts: ReceiptRepository,
    private val returnReceipts: ReturnReceiptRepository,
    private val disputes: DisputeRepository,
    private val previousConditionAnalysis: PreviousConditionAnalysisService,
    private val evidenceCollector: ReportEvidenceCollector,
    private val sanitizer: ReportTextSanitizer
) : ReportTargetContextContributor {
    override val targetType: ReportTargetType = ReportTargetType.RENTAL

    // 거래 신고는 결제·배송·수령·반납·분쟁 정보를 하나의 검토 컨텍스트로 구성합니다.
    override fun contribute(report: Report, reviewedDescription: String?, draft: ReportAnalysisDraft) {
        val rental = rentals.findById(report.targetId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

        addRentalContext(rental, "DIRECT_REPORT_TARGET", true, draft)
    }

    // 직접 거래 신고뿐 아니라 회원·장비 신고에서 발견한 연관 거래에도 같은 수집 규칙을 재사용합니다.
    fun addRentalContext(rental: Rental, relation: String, includeListingImages: Boolean, draft: ReportAnalysisDraft) {
        draft.addFact("rentalRelation", relation)
        draft.addFact("rentalStatus", rental.status)
        draft.addFact("rentalStartDate", rental.startDate)
        draft.addFact("rentalEndDate", rental.endDate)
        draft.addFact("rentalDays", rental.rentalDays)
        draft.addFact("dailyPriceSnapshot", rental.dailyPriceSnapshot)
        draft.addFact("totalPrice", rental.totalPrice)
        sanitizer.addPublicContent(draft.publicContent, "rentalProductName", rental.productNameSnapshot)
        sanitizer.addPublicContent(draft.publicContent, "rentalCategory", rental.categorySnapshot)

        // 성공한 반납 비교 결과가 있으면 동일 사진을 다시 보내지 않고 기존 분석을 재사용합니다.
        val previousResult = previousConditionAnalysis.findSucceededResult(rental.id!!)
        val hasPriorConditionAnalysis = previousResult.isPresent

        if (hasPriorConditionAnalysis) {
            draft.priorConditionAnalysis.putAll(previousResult.get())
        }

        equipment.findById(rental.equipmentId).ifPresent { item ->
            draft.addFact("equipmentStatus", item.status)
            draft.addFact("listedCondition", item.productCondition)
            sanitizer.addPublicContent(draft.publicContent, "equipmentName", item.name)
            sanitizer.addPublicContent(draft.publicContent, "equipmentDescription", item.description)
            sanitizer.addPublicContent(draft.publicContent, "equipmentConditionDetail", item.conditionDetail)

            if (includeListingImages && !hasPriorConditionAnalysis) {
                evidenceCollector.collectEquipmentImages(requireNotNull(item.id), draft, 2)
            }
        }

        payments.findByRentalId(rental.id!!).ifPresent { payment -> addPayment(payment, draft) }

        for (delivery in shipping.findByRentalId(rental.id!!)) {
            addShipping(delivery, draft)
        }

        receipts.findByRentalId(rental.id!!).ifPresent { receipt ->
            addReceipt(receipt, draft, !hasPriorConditionAnalysis)
        }
        returnReceipts.findByRentalId(rental.id!!).ifPresent { receipt ->
            addReturnReceipt(receipt, draft, !hasPriorConditionAnalysis)
        }
        disputes.findFirstByRentalIdOrderByCreatedAtDesc(rental.id!!).ifPresent { dispute ->
            addDispute(dispute, draft)
        }
    }

    private fun addPayment(payment: Payment, draft: ReportAnalysisDraft) {
        draft.addFact("paymentStatus", payment.status)
        draft.addFact("paymentAmount", payment.amount)
    }

    private fun addShipping(delivery: Shipping, draft: ReportAnalysisDraft) {
        val phase = if (delivery.type == ShippingType.OUTBOUND) "outbound" else "return"
        draft.addFact("${phase}ShippingStatus", delivery.status)
        draft.addFact("${phase}DeliveredAt", delivery.deliveredAt)
    }

    private fun addReceipt(receipt: Receipt, draft: ReportAnalysisDraft, includeImages: Boolean) {
        draft.addFact("receiptCondition", receipt.productCondition)
        draft.addFact("receivedAt", receipt.receivedAt)
        sanitizer.addPublicContent(draft.publicContent, "receiptConditionDetail", receipt.conditionDetail)

        if (includeImages) {
            evidenceCollector.collectReceiptImages(receipt, draft)
        }
    }

    private fun addReturnReceipt(receipt: ReturnReceipt, draft: ReportAnalysisDraft, includeImages: Boolean) {
        draft.addFact("returnCondition", receipt.productCondition)
        draft.addFact("returnDate", receipt.returnDate)
        sanitizer.addPublicContent(draft.publicContent, "returnConditionDetail", receipt.conditionDetail)

        if (includeImages) {
            evidenceCollector.collectReturnReceiptImages(receipt, draft)
        }
    }

    private fun addDispute(dispute: Dispute, draft: ReportAnalysisDraft) {
        draft.addFact("relatedDisputeStatus", dispute.status)
        sanitizer.addPublicContent(draft.publicContent, "relatedDisputeReason", dispute.reason)
        sanitizer.addPublicContent(draft.publicContent, "relatedDisputeDescription", dispute.description)
    }
}
