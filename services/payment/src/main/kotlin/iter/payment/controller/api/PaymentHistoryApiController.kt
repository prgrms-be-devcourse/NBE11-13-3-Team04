package iter.payment.controller.api

import iter.common.dto.response.PageResponse
import iter.common.security.CustomUserDetails
import iter.payment.controller.api.spec.PaymentHistoryApiSpec
import iter.payment.dto.request.PaymentHistorySearchRequest
import iter.payment.dto.response.PaymentHistoryResponse
import iter.payment.service.PaymentHistoryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/payments")
class PaymentHistoryApiController(
    private val paymentHistoryService: PaymentHistoryService,
) : PaymentHistoryApiSpec {

    @GetMapping
    override fun getMyPaymentHistory(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @ModelAttribute request: PaymentHistorySearchRequest,
    ): ResponseEntity<PageResponse<PaymentHistoryResponse>> {
        return ResponseEntity.ok(
            paymentHistoryService.getMyPaymentHistory(principal.user.id, request),
        )
    }
}
