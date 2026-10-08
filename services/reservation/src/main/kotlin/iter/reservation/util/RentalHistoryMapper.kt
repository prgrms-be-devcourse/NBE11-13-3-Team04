package iter.reservation.util

import iter.auth.api.UserSummary
import iter.reservation.domain.entity.Rental
import iter.reservation.dto.response.RentalHistoryResponse
import org.springframework.stereotype.Component

@Component
class RentalHistoryMapper {

    // 대여 거래와 상대방 정보를 대여 이력 목록 응답으로 변환합니다.
    fun toResponse(
        rental: Rental,
        counterparty: UserSummary,
        thumbnailUrl: String?,
        overdueDays: Int,
    ): RentalHistoryResponse =
        RentalHistoryResponse(
            rental.id,
            rental.equipmentId,
            rental.productNameSnapshot,
            thumbnailUrl,
            counterparty,
            rental.startDate,
            rental.endDate,
            rental.totalPrice,
            rental.status,
            overdueDays,
        )
}
