package iter.device.dto.request

import iter.device.domain.entity.EquipmentCategory
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

import java.math.BigDecimal
import java.time.LocalDate

data class EquipmentSearchRequest(
    @field:Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
    val keyword: String?,

    val category: EquipmentCategory?,

    @field:DecimalMin(value = "0", message = "최소 가격은 0 이상이어야 합니다.")
    val minPrice: BigDecimal?,

    @field:DecimalMin(value = "0", message = "최대 가격은 0 이상이어야 합니다.")
    val maxPrice: BigDecimal?,

    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val sort: EquipmentSort = EquipmentSort.LATEST,

    @field:Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
    val page: Int = 0,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    val size: Int = 20,
) {
    @get:AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
    val isValidPriceRange: Boolean
        get() = minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0

    @get:AssertTrue(message = "대여 시작일은 오늘 이후이고 종료일은 시작일 이후여야 합니다.")
    val isValidRentalPeriod: Boolean
        get() {
            if (startDate == null && endDate == null) {
                return true
            }
            return startDate != null &&
                endDate != null &&
                startDate.isAfter(LocalDate.now()) &&
                startDate.isBefore(endDate)
        }
}
