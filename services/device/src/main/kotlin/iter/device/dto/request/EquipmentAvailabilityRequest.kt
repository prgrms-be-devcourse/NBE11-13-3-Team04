package iter.device.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotNull

import java.time.LocalDate

data class EquipmentAvailabilityRequest(
    @field:NotNull(message = "대여 시작일과 종료일을 올바르게 입력해주세요.")
    @field:Future(message = "대여 시작일과 종료일을 올바르게 입력해주세요.")
    val startDate: LocalDate?,

    @field:NotNull(message = "대여 시작일과 종료일을 올바르게 입력해주세요.")
    val endDate: LocalDate?,
) {
    @get:AssertTrue(message = "대여 시작일과 종료일을 올바르게 입력해주세요.")
    val isValidRentalPeriod: Boolean
        get() = startDate == null || endDate == null || endDate.isAfter(startDate)
}
