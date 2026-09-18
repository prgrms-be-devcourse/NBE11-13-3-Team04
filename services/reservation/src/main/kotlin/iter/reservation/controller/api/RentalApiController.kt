package iter.reservation.controller.api

import iter.common.security.CustomUserDetails
import iter.common.security.Role
import iter.common.dto.response.PageResponse
import iter.reservation.api.RentalStatus
import iter.reservation.controller.api.spec.RentalApiSpec
import iter.reservation.dto.request.RentalCreateRequest
import iter.reservation.dto.request.RentalRejectRequest
import iter.reservation.dto.response.RentalApproveResponse
import iter.reservation.dto.response.RentalCancelResponse
import iter.reservation.dto.response.RentalCreateResponse
import iter.reservation.dto.response.RentalDetailResponse
import iter.reservation.dto.response.RentalReceivedItemResponse
import iter.reservation.dto.response.RentalRejectResponse
import iter.reservation.service.RentalService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rentals")
class RentalApiController(
    private val rentalService: RentalService,
) : RentalApiSpec {

    @PostMapping
    override fun createRental(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody request: RentalCreateRequest,
    ): ResponseEntity<RentalCreateResponse> {
        val response = rentalService.createRental(principal.user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{rentalId}")
    override fun getRentalDetail(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
    ): ResponseEntity<RentalDetailResponse> {
        val response = rentalService.getRentalDetail(
            rentalId, principal.user.id, principal.user.role == Role.ADMIN,
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping(params = ["role=owner"])
    override fun getReceivedRentals(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) status: RentalStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<RentalReceivedItemResponse>> {
        val response = rentalService.getReceivedRentals(principal.user.id, status, page, size)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{rentalId}/cancel")
    override fun cancelRental(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
    ): ResponseEntity<RentalCancelResponse> {
        val response = rentalService.cancelRental(
            rentalId, principal.user.id, principal.user.role == Role.ADMIN,
        )
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{rentalId}/approve")
    override fun approveRental(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
    ): ResponseEntity<RentalApproveResponse> {
        val response = rentalService.approveRental(
            rentalId, principal.user.id, principal.user.role == Role.ADMIN,
        )
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{rentalId}/reject")
    override fun rejectRental(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @RequestBody request: RentalRejectRequest,
    ): ResponseEntity<RentalRejectResponse> {
        val response = rentalService.rejectRental(
            rentalId, principal.user.id, principal.user.role == Role.ADMIN, request.reason(),
        )
        return ResponseEntity.ok(response)
    }
}
