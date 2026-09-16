package com.example.iter.reservation.dto.response

import com.example.iter.auth.api.UserSummary
import com.example.iter.payment.api.PaymentStatus
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class RentalDetailResponse(
    val rentalId: Long?,
    val equipment: RentalEquipmentSnapshotResponse?,
    val owner: UserSummary?,
    val renter: UserSummary?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val rentalDays: Int,
    val totalPrice: BigDecimal?,
    val paymentStatus: PaymentStatus?,
    val receiverName: String?,
    val receiverPhone: String?,
    val zipcode: String?,
    val address: String?,
    val detailAddress: String?,
    val requestMessage: String?,
    val status: RentalStatus?,
    val overdueDays: Int,
    val createdAt: LocalDateTime?,
) {
    fun rentalId(): Long? = rentalId
    fun equipment(): RentalEquipmentSnapshotResponse? = equipment
    fun owner(): UserSummary? = owner
    fun renter(): UserSummary? = renter
    fun startDate(): LocalDate? = startDate
    fun endDate(): LocalDate? = endDate
    fun rentalDays(): Int = rentalDays
    fun totalPrice(): BigDecimal? = totalPrice
    fun paymentStatus(): PaymentStatus? = paymentStatus
    fun receiverName(): String? = receiverName
    fun receiverPhone(): String? = receiverPhone
    fun zipcode(): String? = zipcode
    fun address(): String? = address
    fun detailAddress(): String? = detailAddress
    fun requestMessage(): String? = requestMessage
    fun status(): RentalStatus? = status
    fun overdueDays(): Int = overdueDays
    fun createdAt(): LocalDateTime? = createdAt

    companion object {
        // paymentStatus는 아직 결제 전(PENDING) 예약이면 null — 결제 전 상태도 조회 가능해야 하므로 null 허용
        @JvmStatic
        fun of(
            rental: Rental,
            renter: UserSummary?,
            owner: UserSummary?,
            paymentStatus: PaymentStatus?,
            overdueDays: Int,
        ): RentalDetailResponse =
            RentalDetailResponse(
                rental.id,
                RentalEquipmentSnapshotResponse.from(rental),
                owner,
                renter,
                rental.startDate,
                rental.endDate,
                rental.rentalDays,
                rental.totalPrice,
                paymentStatus,
                rental.receiverName,
                rental.receiverPhone,
                rental.zipcode,
                rental.address,
                rental.detailAddress,
                rental.requestMessage,
                rental.status,
                overdueDays,
                rental.createdAt,
            )
    }
}
