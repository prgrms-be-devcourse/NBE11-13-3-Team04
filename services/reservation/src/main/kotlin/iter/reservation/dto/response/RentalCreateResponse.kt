package iter.reservation.dto.response

import iter.reservation.api.RentalStatus
import iter.reservation.domain.entity.Rental

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class RentalCreateResponse(
    val rentalId: Long?,
    val equipmentId: Long?,
    val status: RentalStatus?,
    val productNameSnapshot: String?,
    val categorySnapshot: String?,
    val dailyPriceSnapshot: BigDecimal?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val rentalDays: Int,
    val totalPrice: BigDecimal?,
    val createdAt: LocalDateTime?,
) {
    fun rentalId(): Long? = rentalId
    fun equipmentId(): Long? = equipmentId
    fun status(): RentalStatus? = status
    fun productNameSnapshot(): String? = productNameSnapshot
    fun categorySnapshot(): String? = categorySnapshot
    fun dailyPriceSnapshot(): BigDecimal? = dailyPriceSnapshot
    fun startDate(): LocalDate? = startDate
    fun endDate(): LocalDate? = endDate
    fun rentalDays(): Int = rentalDays
    fun totalPrice(): BigDecimal? = totalPrice
    fun createdAt(): LocalDateTime? = createdAt

    companion object {
        @JvmStatic
        fun from(rental: Rental): RentalCreateResponse =
            RentalCreateResponse(
                rental.id,
                rental.equipmentId,
                rental.status,
                rental.productNameSnapshot,
                rental.categorySnapshot,
                rental.dailyPriceSnapshot,
                rental.startDate,
                rental.endDate,
                rental.rentalDays,
                rental.totalPrice,
                rental.createdAt,
            )
    }
}
