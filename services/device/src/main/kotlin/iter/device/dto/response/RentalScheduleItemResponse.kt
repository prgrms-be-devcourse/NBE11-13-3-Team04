package iter.device.dto.response

import iter.reservation.api.RentalScheduleItem
import iter.reservation.api.RentalStatus

import java.time.LocalDate

data class RentalScheduleItemResponse(
    val rentalId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: RentalStatus,
) {
    companion object {
        @JvmStatic
        fun from(item: RentalScheduleItem): RentalScheduleItemResponse =
            RentalScheduleItemResponse(
                rentalId = item.rentalId,
                startDate = item.startDate,
                endDate = item.endDate,
                status = item.status,
            )
    }
}
