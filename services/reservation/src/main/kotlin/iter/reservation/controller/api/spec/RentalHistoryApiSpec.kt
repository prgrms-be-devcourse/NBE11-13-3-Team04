package iter.reservation.controller.api.spec

import iter.common.dto.request.PagingRequest
import iter.common.dto.response.PageResponse
import iter.common.security.CustomUserDetails
import iter.reservation.dto.request.RentalHistorySearchRequest
import iter.reservation.dto.response.RentalHistoryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity

@Tag(name = "Rental History", description = "빌린·빌려준 장비 및 연체 이력 조회 API")
@SecurityRequirement(name = "JWT")
interface RentalHistoryApiSpec {

    @Operation(
        summary = "빌린 장비 이력 조회",
        description = "로그인 사용자가 대여자인 거래를 상태와 예약 당시 장비명으로 검색합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "빌린 장비 이력 조회 성공"),
            ApiResponse(responseCode = "400", description = "검색 또는 페이징 조건 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ],
    )
    fun getBorrowedHistory(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Valid @ParameterObject request: RentalHistorySearchRequest,
    ): ResponseEntity<PageResponse<RentalHistoryResponse>>

    @Operation(
        summary = "빌려준 장비 이력 조회",
        description = "로그인 사용자가 등록한 장비의 대여 거래를 상태와 예약 당시 장비명으로 검색합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "빌려준 장비 이력 조회 성공"),
            ApiResponse(responseCode = "400", description = "검색 또는 페이징 조건 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ],
    )
    fun getLentHistory(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Valid @ParameterObject request: RentalHistorySearchRequest,
    ): ResponseEntity<PageResponse<RentalHistoryResponse>>

    @Operation(
        summary = "빌린 장비 연체 이력 조회",
        description = "로그인 사용자가 빌린 거래 중 종료일이 지났고 아직 정상 반환되지 않은 거래를 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "빌린 장비 연체 이력 조회 성공"),
            ApiResponse(responseCode = "400", description = "페이징 조건 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ],
    )
    fun getBorrowedOverdueHistory(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Valid @ParameterObject request: PagingRequest,
    ): ResponseEntity<PageResponse<RentalHistoryResponse>>

    @Operation(
        summary = "빌려준 장비 연체 이력 조회",
        description = "로그인 사용자가 빌려준 거래 중 종료일이 지났고 아직 정상 반환되지 않은 거래를 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "빌려준 장비 연체 이력 조회 성공"),
            ApiResponse(responseCode = "400", description = "페이징 조건 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ],
    )
    fun getLentOverdueHistory(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Valid @ParameterObject request: PagingRequest,
    ): ResponseEntity<PageResponse<RentalHistoryResponse>>
}
