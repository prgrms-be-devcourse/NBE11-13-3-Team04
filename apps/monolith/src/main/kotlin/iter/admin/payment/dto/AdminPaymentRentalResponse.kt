package iter.admin.payment.dto

import iter.reservation.api.RentalStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.jvm.JvmRecord

@JvmRecord
data class AdminPaymentRentalResponse(
    val equipmentId: Long?,
    val equipmentName: String?,
    val category: String?,
    val dailyPrice: BigDecimal?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val rentalDays: Int?,
    val totalPrice: BigDecimal?,
    val rentalStatus: RentalStatus?
)
