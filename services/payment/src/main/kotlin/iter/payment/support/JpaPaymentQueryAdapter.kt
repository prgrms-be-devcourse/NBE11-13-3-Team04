package iter.payment.support

import iter.payment.api.PaymentQueryPort
import iter.payment.api.PaymentStatus
import iter.payment.domain.repository.PaymentRepository
import iter.payment.service.model.RentalPaymentStatusRow
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

// payment/api/PaymentQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
//
// "환불됨 = REFUNDED" 라는 판단이 여기 있다. 이전에는 알림 리스너가
// PaymentStatus 를 직접 꺼내 비교했다.
@Component
class JpaPaymentQueryAdapter(
    private val paymentRepository: PaymentRepository,
) : PaymentQueryPort {

    @Transactional(readOnly = true)
    override fun count(): Long = paymentRepository.count()

    @Transactional(readOnly = true)
    override fun findStatusByRentalId(rentalId: Long?): Optional<PaymentStatus> {
        val rentalId = rentalId!!
        return paymentRepository.findByRentalId(rentalId).map { it.status }
    }

    @Transactional(readOnly = true)
    override fun findStatusesByRentalIds(rentalIds: Collection<Long>): Map<Long, PaymentStatus> {
        if (rentalIds.isEmpty()) {
            return emptyMap()
        }
        return paymentRepository.findStatusesByRentalIdIn(rentalIds)
            .associateBy(RentalPaymentStatusRow::rentalId, RentalPaymentStatusRow::paymentStatus)
    }

    @Transactional(readOnly = true)
    override fun isRefundedForRental(rentalId: Long?): Boolean {
        val rentalId = rentalId!!
        return paymentRepository.findByRentalId(rentalId)
            .map { it.status }
            .map { status -> status == PaymentStatus.REFUNDED }
            .orElse(false)
    }
}
