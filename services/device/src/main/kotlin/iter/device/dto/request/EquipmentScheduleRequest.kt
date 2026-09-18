package iter.device.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat

import java.time.LocalDate

data class EquipmentScheduleRequest(
    @field:NotNull(message = "조회 시작일은 필수입니다.")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val from: LocalDate?,

    @field:NotNull(message = "조회 종료일은 필수입니다.")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val to: LocalDate?,
) {
    @get:AssertTrue(message = "조회 시작일은 종료일보다 늦을 수 없습니다.")
    val isValidPeriod: Boolean
        get() = from == null || to == null || !from.isAfter(to)
}
