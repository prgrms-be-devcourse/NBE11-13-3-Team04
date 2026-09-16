package com.example.iter.reservation.util

import com.example.iter.auth.api.UserSummary
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.dto.response.RentalHistoryResponse
import org.springframework.stereotype.Component

@Component
class RentalHistoryMapper {
    fun toResponse(rental: Rental, counterparty: UserSummary, thumbnailUrl: String?, overdueDays: Int): RentalHistoryResponse = RentalHistoryResponse(
        rental.id,
        rental.equipmentId,
        rental.productNameSnapshot,
        thumbnailUrl,
        counterparty,
        rental.startDate,
        rental.endDate,
        rental.totalPrice,
        rental.status,
        overdueDays
    )
}
