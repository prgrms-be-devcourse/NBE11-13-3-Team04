package iter.reservation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

import java.time.LocalDate

data class RentalCreateRequest(
    @field:NotNull(message = "장비 ID는 필수입니다.")
    val equipmentId: Long?,

    @field:NotNull(message = "대여 시작일은 필수입니다.")
    val startDate: LocalDate?,

    @field:NotNull(message = "대여 종료일은 필수입니다.")
    val endDate: LocalDate?,

    @field:NotBlank(message = "수령인 이름은 필수입니다.")
    @field:Size(max = 20, message = "수령인 이름은 20자 이하여야 합니다.")
    val receiverName: String?,

    @field:NotBlank(message = "수령인 연락처는 필수입니다.")
    @field:Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    val receiverPhone: String?,

    @field:NotBlank(message = "우편번호는 필수입니다.")
    @field:Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
    val zipcode: String?,

    @field:NotBlank(message = "주소는 필수입니다.")
    @field:Size(max = 200, message = "주소는 200자 이하여야 합니다.")
    val address: String?,

    @field:Size(max = 200, message = "상세주소는 200자 이하여야 합니다.")
    val detailAddress: String?,

    val requestMessage: String?,

    val useDefaultAddress: Boolean?,
) {
    // RentalService(자바)가 record 접근자 스타일로 그대로 부른다.
    fun equipmentId(): Long? = equipmentId
    fun startDate(): LocalDate? = startDate
    fun endDate(): LocalDate? = endDate
    fun receiverName(): String? = receiverName
    fun receiverPhone(): String? = receiverPhone
    fun zipcode(): String? = zipcode
    fun address(): String? = address
    fun detailAddress(): String? = detailAddress
    fun requestMessage(): String? = requestMessage
    fun useDefaultAddress(): Boolean? = useDefaultAddress
}
