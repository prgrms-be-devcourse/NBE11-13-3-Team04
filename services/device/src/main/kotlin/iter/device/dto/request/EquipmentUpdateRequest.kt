package iter.device.dto.request

import iter.device.domain.entity.ProductConditionType
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

import java.math.BigDecimal
import java.time.LocalDate

data class EquipmentUpdateRequest(
    @field:Size(max = 100, message = "장비명은 100자 이하여야 합니다.")
    val name: String?,
    val description: String?,
    @field:Positive(message = "1일 대여 가격은 0보다 커야 합니다.")
    val dailyPrice: BigDecimal?,
    @field:FutureOrPresent(message = "대여 가능 시작일은 현재 날짜 이상이어야 합니다.")
    val availableFrom: LocalDate?,
    val availableTo: LocalDate?,
    val productCondition: ProductConditionType?,
    val conditionDetail: String?,
) {
    @get:AssertTrue(message = "전달된 문자열은 빈 값일 수 없습니다.")
    val isValidStrings: Boolean
        get() = (name == null || name.isNotBlank()) &&
            (description == null || description.isNotBlank()) &&
            (conditionDetail == null || conditionDetail.isNotBlank())

    @AssertTrue(message = "변경할 필드를 하나 이상 입력해주세요.")
    fun hasChanges(): Boolean =
        name != null ||
            description != null ||
            dailyPrice != null ||
            availableFrom != null ||
            availableTo != null ||
            productCondition != null ||
            conditionDetail != null
}
