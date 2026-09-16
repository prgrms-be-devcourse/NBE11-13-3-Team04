package com.example.iter.reservation.controller.api

import com.example.iter.common.dto.request.PagingRequest
import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.security.CustomUserDetails
import com.example.iter.reservation.controller.api.spec.ReturnApiSpec
import com.example.iter.reservation.dto.request.ReturnConfirmationRequest
import com.example.iter.reservation.dto.response.ReturnComparisonResponse
import com.example.iter.reservation.dto.response.ReturnConfirmationResponse
import com.example.iter.reservation.dto.response.ReturnTargetResponse
import com.example.iter.reservation.service.ReturnService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/rentals")
@PreAuthorize("hasRole('USER')")
class ReturnApiController(private val returnService: ReturnService) : ReturnApiSpec {

    @GetMapping("/returns")
    override fun getReturnTargets(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @ModelAttribute
        request: PagingRequest
    ): ResponseEntity<PageResponse<ReturnTargetResponse>> =
        ResponseEntity.ok(returnService.getReturnTargets(principal.user.id, request))

    @GetMapping("/{rentalId}/return-comparison")
    override fun getReturnComparison(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        rentalId: Long
    ): ResponseEntity<ReturnComparisonResponse> =
        ResponseEntity.ok(returnService.getReturnComparison(principal.user.id, rentalId))

    @PostMapping("/{rentalId}/return-confirmation")
    override fun confirmReturn(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @PathVariable
        rentalId: Long,

        @RequestBody
        request: ReturnConfirmationRequest
    ): ResponseEntity<ReturnConfirmationResponse> =
        ResponseEntity.ok(returnService.confirmReturn(principal.user.id, rentalId, request))
}
