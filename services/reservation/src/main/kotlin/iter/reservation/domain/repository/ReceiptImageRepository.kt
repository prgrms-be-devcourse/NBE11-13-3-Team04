package iter.reservation.domain.repository

import iter.reservation.domain.entity.ReceiptImage
import org.springframework.data.jpa.repository.JpaRepository

interface ReceiptImageRepository : JpaRepository<ReceiptImage, Long> {
    // 수령 증빙 이미지를 등록 순서대로 조회합니다.
    fun findByReceipt_IdOrderBySortOrderAscIdAsc(receiptId: Long): List<ReceiptImage>
}
