package iter.reservation.domain.repository

import iter.reservation.domain.entity.ReturnReceiptImage
import org.springframework.data.jpa.repository.JpaRepository

interface ReturnReceiptImageRepository : JpaRepository<ReturnReceiptImage, Long> {
    // 반납 증빙 이미지를 등록 순서대로 조회합니다.
    fun findByReturnReceipt_IdOrderBySortOrderAscIdAsc(returnReceiptId: Long): List<ReturnReceiptImage>
}
