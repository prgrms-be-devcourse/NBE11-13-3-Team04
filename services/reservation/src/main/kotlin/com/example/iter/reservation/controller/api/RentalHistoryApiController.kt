package com.example.iter.reservation.controller.api

import com.example.iter.common.dto.request.PagingRequest
import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.security.CustomUserDetails
import com.example.iter.reservation.controller.api.spec.RentalHistoryApiSpec
import com.example.iter.reservation.dto.request.RentalHistorySearchRequest
import com.example.iter.reservation.dto.response.RentalHistoryResponse
import com.example.iter.reservation.service.RentalHistoryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/rentals")
class RentalHistoryApiController(private val rentalHistoryService: RentalHistoryService) : RentalHistoryApiSpec {

    @GetMapping("/borrowed")
    override fun getBorrowedHistory(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @ModelAttribute
        request: RentalHistorySearchRequest
    ): ResponseEntity<PageResponse<RentalHistoryResponse>> =
        ResponseEntity.ok(rentalHistoryService.getBorrowedHistory(principal.user.id, request))

    @GetMapping("/lent")
    override fun getLentHistory(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @ModelAttribute
        request: RentalHistorySearchRequest
    ): ResponseEntity<PageResponse<RentalHistoryResponse>> =
        ResponseEntity.ok(rentalHistoryService.getLentHistory(principal.user.id, request))

    @GetMapping("/borrowed/overdue")
    override fun getBorrowedOverdueHistory(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @ModelAttribute
        request: PagingRequest
    ): ResponseEntity<PageResponse<RentalHistoryResponse>> =
        ResponseEntity.ok(rentalHistoryService.getBorrowedOverdueHistory(principal.user.id, request))

    @GetMapping("/lent/overdue")
    override fun getLentOverdueHistory(
        @AuthenticationPrincipal
        principal: CustomUserDetails,

        @ModelAttribute
        request: PagingRequest
    ): ResponseEntity<PageResponse<RentalHistoryResponse>> =
        ResponseEntity.ok(rentalHistoryService.getLentOverdueHistory(principal.user.id, request))
}
