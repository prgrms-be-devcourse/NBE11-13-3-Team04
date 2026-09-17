package com.example.iter.payment.service

import com.example.iter.common.dto.response.PageResponse
import com.example.iter.payment.domain.entity.Payment
import com.example.iter.payment.domain.repository.PaymentRepository
import com.example.iter.payment.dto.request.PaymentHistorySearchRequest
import com.example.iter.payment.dto.response.PaymentHistoryResponse
import com.example.iter.reservation.api.RentalQueryPort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentHistoryService(
    private val paymentRepository: PaymentRepository,
    private val rentalQueryPort: RentalQueryPort,
) {

    fun getMyPaymentHistory(userId: Long, request: PaymentHistorySearchRequest): PageResponse<PaymentHistoryResponse> {
        val payments = paymentRepository.findMyPaymentHistory(
            userId,
            request.status,
            PageRequest.of(request.page, request.size),
        )

        val rentalsById = rentalQueryPort.findAll(payments.map(Payment::rentalId).toList())

        return PageResponse.from(
            payments.map { payment -> PaymentHistoryResponse.of(payment, rentalsById[payment.rentalId]!!) },
        )
    }
}
