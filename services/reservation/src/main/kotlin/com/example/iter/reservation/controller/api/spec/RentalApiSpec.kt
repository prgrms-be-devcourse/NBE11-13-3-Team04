package com.example.iter.reservation.controller.api.spec

import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.security.CustomUserDetails
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.dto.request.RentalCreateRequest
import com.example.iter.reservation.dto.request.RentalRejectRequest
import com.example.iter.reservation.dto.response.RentalApproveResponse
import com.example.iter.reservation.dto.response.RentalCancelResponse
import com.example.iter.reservation.dto.response.RentalCreateResponse
import com.example.iter.reservation.dto.response.RentalDetailResponse
import com.example.iter.reservation.dto.response.RentalReceivedItemResponse
import com.example.iter.reservation.dto.response.RentalRejectResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity

@Tag(name = "Rental", description = "대여 요청 API")
@SecurityRequirement(name = "JWT")
interface RentalApiSpec {

    @Operation(summary = "대여 요청 생성")
    fun createRental(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Valid request: RentalCreateRequest,
    ): ResponseEntity<RentalCreateResponse>

    @Operation(summary = "대여 요청 상세 조회 (+ 연체 여부)")
    fun getRentalDetail(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true) rentalId: Long,
    ): ResponseEntity<RentalDetailResponse>

    @Operation(summary = "받은 요청 목록")
    fun getReceivedRentals(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "필터링할 대여 상태") status: RentalStatus?,
        @Parameter(description = "페이지 번호 (0부터 시작)") page: Int,
        @Parameter(description = "페이지 크기") size: Int,
    ): ResponseEntity<PageResponse<RentalReceivedItemResponse>>

    @Operation(summary = "승인 전 예약 취소")
    fun cancelRental(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true) rentalId: Long,
    ): ResponseEntity<RentalCancelResponse>

    @Operation(summary = "대여 요청 승인 (비관적 락)")
    fun approveRental(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true) rentalId: Long,
    ): ResponseEntity<RentalApproveResponse>

    @Operation(summary = "대여 요청 거절")
    fun rejectRental(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true) rentalId: Long,
        @Valid request: RentalRejectRequest,
    ): ResponseEntity<RentalRejectResponse>
}
