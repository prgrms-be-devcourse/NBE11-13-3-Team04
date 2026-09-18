package iter.admin.payment.util

import iter.admin.payment.dto.AdminPaymentDetailResponse
import iter.admin.payment.dto.AdminPaymentRentalResponse
import iter.admin.payment.dto.AdminPaymentSummaryResponse
import iter.admin.payment.model.AdminPaymentDetailRow
import iter.admin.payment.model.AdminPaymentSummaryRow
import org.springframework.stereotype.Component

@Component
class AdminPaymentMapper {
    fun toSummary(row: AdminPaymentSummaryRow): AdminPaymentSummaryResponse = AdminPaymentSummaryResponse(
        row.paymentId,
        row.rentalId,
        row.orderId,
        row.renterId,
        row.renterEmail,
        row.renterName,
        row.renterNickname,
        row.equipmentName,
        row.amount,
        row.paymentStatus,
        row.rentalStatus,
        row.paidAt,
        row.refundedAt,
        row.createdAt
    )

    fun toDetail(row: AdminPaymentDetailRow): AdminPaymentDetailResponse = AdminPaymentDetailResponse(
        AdminPaymentSummaryResponse(
            row.paymentId,
            row.rentalId,
            row.orderId,
            row.renterId,
            row.renterEmail,
            row.renterName,
            row.renterNickname,
            row.equipmentName,
            row.amount,
            row.paymentStatus,
            row.rentalStatus,
            row.paidAt,
            row.refundedAt,
            row.createdAt
        ),

        AdminPaymentRentalResponse(
            row.equipmentId,
            row.equipmentName,
            row.category,
            row.dailyPrice,
            row.startDate,
            row.endDate,
            row.rentalDays,
            row.totalPrice,
            row.rentalStatus
        ),

        row.updatedAt
    )
}
