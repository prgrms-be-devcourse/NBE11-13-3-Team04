package iter.reservation.controller.api.spec

import iter.common.security.CustomUserDetails
import iter.reservation.dto.request.ReceiptCreateRequest
import iter.reservation.dto.request.ReturnEvidenceCreateRequest
import iter.reservation.dto.request.ShippingRegisterRequest
import iter.reservation.dto.response.ReceiptCreateResponse
import iter.reservation.dto.response.ReturnEvidenceCreateResponse
import iter.reservation.dto.response.ReturnRequestResponse
import iter.reservation.dto.response.ShippingRegisterResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity

@Tag(name = "Rental Fulfillment", description = "배송 등록/수령 확인/반납 신청/반납 증빙 제출 API")
@SecurityRequirement(name = "JWT")
interface RentalFulfillmentApiSpec {

    @Operation(
        summary = "출고 배송 등록 (등록자)",
        description = "APPROVED 상태의 대여 건에 등록자가 출고 배송 정보를 등록합니다. APPROVED -> SHIPPING",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "배송 등록 성공"),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "403", description = "장비 등록자가 아님", content = [Content()]),
            ApiResponse(responseCode = "404", description = "거래 또는 장비 없음", content = [Content()]),
            ApiResponse(responseCode = "409", description = "APPROVED 상태가 아님", content = [Content()]),
        ],
    )
    fun registerShipping(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true)
        @Positive(message = "대여 ID는 1 이상이어야 합니다.")
        rentalId: Long,
        @Valid request: ShippingRegisterRequest,
    ): ResponseEntity<ShippingRegisterResponse>

    @Operation(
        summary = "수령 증빙 제출 (대여자)",
        description = "SHIPPING 상태의 대여 건에 대여자가 수령 시점 상태·사진을 제출합니다. SHIPPING -> RENTING",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "수령 확인 성공"),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "403", description = "거래 당사자(대여자)가 아님", content = [Content()]),
            ApiResponse(responseCode = "404", description = "거래 없음", content = [Content()]),
            ApiResponse(responseCode = "409", description = "SHIPPING 상태가 아님", content = [Content()]),
        ],
    )
    fun createReceipt(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true)
        @Positive(message = "대여 ID는 1 이상이어야 합니다.")
        rentalId: Long,
        @Valid request: ReceiptCreateRequest,
    ): ResponseEntity<ReceiptCreateResponse>

    @Operation(
        summary = "반납 신청 (대여자)",
        description = "RENTING 상태의 대여 건에 대해 대여자가 반납을 신청합니다. RENTING -> RETURN_REQUESTED",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "반납 신청 성공"),
            ApiResponse(responseCode = "400", description = "대여 ID 형식 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "403", description = "거래 당사자(대여자)가 아님", content = [Content()]),
            ApiResponse(responseCode = "404", description = "거래 없음", content = [Content()]),
            ApiResponse(responseCode = "409", description = "RENTING 상태가 아님", content = [Content()]),
        ],
    )
    fun requestReturn(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true)
        @Positive(message = "대여 ID는 1 이상이어야 합니다.")
        rentalId: Long,
    ): ResponseEntity<ReturnRequestResponse>

    @Operation(
        summary = "반납 증빙 제출 (대여자)",
        description = "RETURN_REQUESTED 상태의 대여 건에 대여자가 반납 시점 상태·사진을 제출합니다. " +
            "RETURN_REQUESTED -> RETURNED (이후 등록자의 최종 확인 대상이 됨)",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "반납 증빙 제출 성공"),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "403", description = "거래 당사자(대여자)가 아님", content = [Content()]),
            ApiResponse(responseCode = "404", description = "거래 없음", content = [Content()]),
            ApiResponse(responseCode = "409", description = "RETURN_REQUESTED 상태가 아님", content = [Content()]),
        ],
    )
    fun createReturnEvidence(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "대여 거래 ID", example = "101", required = true)
        @Positive(message = "대여 ID는 1 이상이어야 합니다.")
        rentalId: Long,
        @Valid request: ReturnEvidenceCreateRequest,
    ): ResponseEntity<ReturnEvidenceCreateResponse>
}
