package com.example.iter.reservation.controller.api

import com.example.iter.common.security.CustomUserDetails
import com.example.iter.reservation.controller.api.spec.RentalFulfillmentApiSpec
import com.example.iter.reservation.dto.request.ReceiptCreateRequest
import com.example.iter.reservation.dto.request.ReturnEvidenceCreateRequest
import com.example.iter.reservation.dto.request.ShippingRegisterRequest
import com.example.iter.reservation.dto.response.ReceiptCreateResponse
import com.example.iter.reservation.dto.response.ReturnEvidenceCreateResponse
import com.example.iter.reservation.dto.response.ReturnRequestResponse
import com.example.iter.reservation.dto.response.ShippingRegisterResponse
import com.example.iter.reservation.service.RentalFulfillmentService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 승인 이후 배송/수령/반납 신청/반납증빙 단계 — RentalApiController(요청/승인/거절/취소)와
// ReturnApiController(반납 최종확인)가 다루지 않던 중간 구간을 채운다.
//
// 파라미터 제약(@Positive/@Valid)은 스펙 인터페이스에만 선언한다 — 구현 메서드에서 다시
// 선언하면 Bean Validation이 "오버라이드 메서드가 제약을 재정의했다"며 컨텍스트 초기화 시
// ConstraintDeclarationException을 던진다(HV000151, 실제로 겪고 스펙에서만 남기도록 수정함).
@Validated
@RestController
@RequestMapping("/api/v1/rentals")
@PreAuthorize("hasRole('USER')")
class RentalFulfillmentApiController(
    private val rentalFulfillmentService: RentalFulfillmentService,
) : RentalFulfillmentApiSpec {

    @PostMapping("/{rentalId}/shipping")
    override fun registerShipping(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @RequestBody request: ShippingRegisterRequest,
    ): ResponseEntity<ShippingRegisterResponse> {
        val response = rentalFulfillmentService.registerShipping(principal.user.id, rentalId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{rentalId}/receipt")
    override fun createReceipt(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @RequestBody request: ReceiptCreateRequest,
    ): ResponseEntity<ReceiptCreateResponse> {
        val response = rentalFulfillmentService.createReceipt(principal.user.id, rentalId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{rentalId}/return-request")
    override fun requestReturn(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
    ): ResponseEntity<ReturnRequestResponse> {
        val response = rentalFulfillmentService.requestReturn(principal.user.id, rentalId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{rentalId}/return-evidence")
    override fun createReturnEvidence(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @RequestBody request: ReturnEvidenceCreateRequest,
    ): ResponseEntity<ReturnEvidenceCreateResponse> {
        val response = rentalFulfillmentService.createReturnEvidence(principal.user.id, rentalId, request)
        return ResponseEntity.ok(response)
    }
}
