package com.example.iter.payment.service;

import com.example.iter.common.dto.response.PageResponse;
import com.example.iter.payment.domain.entity.Payment;
import com.example.iter.payment.domain.repository.PaymentRepository;
import com.example.iter.payment.dto.request.PaymentHistorySearchRequest;
import com.example.iter.payment.dto.response.PaymentHistoryResponse;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryService {

    private final PaymentRepository paymentRepository;
    private final RentalQueryPort rentalQueryPort;

    public PageResponse<PaymentHistoryResponse> getMyPaymentHistory(
            Long userId,
            PaymentHistorySearchRequest request
    ) {
        Page<Payment> payments = paymentRepository.findMyPaymentHistory(
                userId,
                request.status(),
                PageRequest.of(request.page(), request.size())
        );

        Map<Long, RentalInfo> rentalsById = rentalQueryPort.findAll(
                payments.map(Payment::getRentalId).toList());

        return PageResponse.from(payments.map(payment ->
                PaymentHistoryResponse.of(payment, rentalsById.get(payment.getRentalId()))));
    }
}
