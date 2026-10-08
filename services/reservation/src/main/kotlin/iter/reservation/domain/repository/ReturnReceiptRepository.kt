package iter.reservation.domain.repository

import iter.reservation.domain.entity.ReturnReceipt
import org.springframework.data.jpa.repository.JpaRepository

import java.util.Optional

interface ReturnReceiptRepository : JpaRepository<ReturnReceipt, Long> {
    fun findByRentalId(rentalId: Long): Optional<ReturnReceipt>

    // 여러 거래의 반납 증빙을 한 번에 조회합니다.
    fun findAllByRental_IdIn(rentalIds: Collection<Long>): List<ReturnReceipt>
}
