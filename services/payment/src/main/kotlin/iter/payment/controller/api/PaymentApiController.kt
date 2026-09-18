package iter.payment.controller.api

import iter.common.security.CustomUserDetails
import iter.payment.dto.request.PaymentConfirmRequest
import iter.payment.dto.response.PaymentConfirmResponse
import iter.payment.dto.response.PaymentReadyResponse
import iter.payment.service.PaymentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Payment", description = "토스페이먼츠 결제 API")
@RestController
@RequestMapping("/api/v1/rentals")
class PaymentApiController(
    private val paymentService: PaymentService,
) {

    @Operation(summary = "결제 준비 (orderId 발급)", security = [SecurityRequirement(name = "JWT")])
    @PostMapping("/{rentalId}/payment/ready")
    fun ready(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable rentalId: Long,
    ): ResponseEntity<PaymentReadyResponse> {
        val response = paymentService.ready(rentalId, principal.user.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{rentalId}/payment/confirm")
    fun confirm(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable rentalId: Long,
        @Valid @RequestBody request: PaymentConfirmRequest,
    ): ResponseEntity<PaymentConfirmResponse> {
        val response = paymentService.confirm(rentalId, principal.user.id, request)
        return ResponseEntity.ok(response)
    }
}
