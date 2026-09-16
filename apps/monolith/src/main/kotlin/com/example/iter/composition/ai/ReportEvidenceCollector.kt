package com.example.iter.composition.ai

import com.example.iter.ai.service.ReportAnalysisContext
import com.example.iter.ai.service.ReportAnalysisImages
import com.example.iter.device.domain.repository.EquipmentImageRepository
import com.example.iter.reservation.domain.entity.Receipt
import com.example.iter.reservation.domain.entity.ReturnReceipt
import com.example.iter.reservation.domain.repository.ReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptImageRepository
import org.springframework.stereotype.Component

@Component
class ReportEvidenceCollector(
    private val equipmentImages: EquipmentImageRepository,
    private val receiptImages: ReceiptImageRepository,
    private val returnReceiptImages: ReturnReceiptImageRepository,
    private val images: ReportAnalysisImages
) {
    // 신고 대상 장비의 대표 사진만 제한적으로 수집해 불필요한 이미지 토큰 사용을 줄입니다.
    fun collectEquipmentImages(equipmentId: Long, draft: ReportAnalysisDraft, limit: Int) {
        val storedImages = equipmentImages.findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)

        for (index in 0 until minOf(storedImages.size, limit)) {
            val stored = storedImages[index]
            val path = stored.objectKey?.takeUnless(String::isBlank) ?: stored.imageUrl
            val slot = stored.captureView?.let { captureView -> "LISTING_${captureView.name}" }
                ?: "LISTING_${index + 1}"
            draft.addImageCandidate(path, "listing-${stored.id}", slot)
        }
    }

    // 수령 증빙은 촬영 순서를 보존하며 최대 세 장까지만 후보에 추가합니다.
    fun collectReceiptImages(receipt: Receipt, draft: ReportAnalysisDraft) {
        val storedImages = receiptImages.findByReceipt_IdOrderBySortOrderAscIdAsc(receipt.id!!)

        for (index in 0 until minOf(storedImages.size, MAX_IMAGES_PER_RECEIPT)) {
            val stored = storedImages[index]
            val slot = stored.captureView?.let { captureView -> "RECEIPT_${captureView.name}" }
                ?: "RECEIPT_${index + 1}"
            draft.addImageCandidate(stored.imageUrl, "receipt-${stored.id}", slot)
        }
    }

    // 반납 증빙은 촬영 순서를 보존하며 최대 세 장까지만 후보에 추가합니다.
    fun collectReturnReceiptImages(receipt: ReturnReceipt, draft: ReportAnalysisDraft) {
        val storedImages = returnReceiptImages.findByReturnReceipt_IdOrderBySortOrderAscIdAsc(receipt.id!!)

        for (index in 0 until minOf(storedImages.size, MAX_IMAGES_PER_RECEIPT)) {
            val stored = storedImages[index]
            val slot = stored.captureView?.let { captureView -> "RETURN_${captureView.name}" }
                ?: "RETURN_${index + 1}"
            draft.addImageCandidate(stored.imageUrl, "return-${stored.id}", slot)
        }
    }

    // DB 트랜잭션 밖에서 S3 객체를 검증하고 유효한 증빙만 전체 여섯 장 한도로 확정합니다.
    fun resolve(draft: ReportAnalysisDraft): ReportAnalysisContext {
        val resolvedImages = ArrayList<Map<String, Any?>>()

        for (candidate in draft.imageCandidates) {
            if (resolvedImages.size >= MAX_IMAGES) {
                break
            }

            images.reference(candidate.path, candidate.imageId, candidate.captureSlot).ifPresent(resolvedImages::add)
        }

        return draft.toContext(resolvedImages)
    }

    private companion object {
        private const val MAX_IMAGES = 6
        private const val MAX_IMAGES_PER_RECEIPT = 3
    }
}
