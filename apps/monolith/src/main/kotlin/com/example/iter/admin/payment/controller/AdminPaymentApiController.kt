package com.example.iter.admin.payment.controller

import com.example.iter.admin.payment.controller.spec.AdminPaymentApiSpec
import com.example.iter.admin.payment.dto.AdminPaymentDetailResponse
import com.example.iter.admin.payment.dto.AdminPaymentSummaryResponse
import com.example.iter.admin.payment.service.AdminPaymentQueryService
import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.payment.dto.request.AdminPaymentSearchRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
class AdminPaymentApiController(private val adminPaymentQueryService: AdminPaymentQueryService) : AdminPaymentApiSpec {
    @GetMapping
    override fun getPayments(@ModelAttribute request: AdminPaymentSearchRequest): ResponseEntity<CursorPageResponse<AdminPaymentSummaryResponse>> =
        ResponseEntity.ok(adminPaymentQueryService.getPayments(request))

    @GetMapping("/{paymentId}")
    override fun getPayment(@PathVariable paymentId: Long): ResponseEntity<AdminPaymentDetailResponse> =
        ResponseEntity.ok(adminPaymentQueryService.getPayment(paymentId))
}
