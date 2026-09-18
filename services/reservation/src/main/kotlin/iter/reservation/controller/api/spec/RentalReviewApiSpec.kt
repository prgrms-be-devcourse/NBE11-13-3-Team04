package iter.reservation.controller.api.spec

import iter.common.dto.response.CursorPageResponse
import iter.common.security.CustomUserDetails
import iter.reservation.dto.request.RentalReviewCreateRequest
import iter.reservation.dto.response.RentalReviewResponse
import iter.reservation.dto.response.UserReviewStatsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity

@Tag(name = "Rental Review", description = "대여 거래 리뷰 API")
interface RentalReviewApiSpec {

    @Operation(
        summary = "거래 리뷰 작성",
        description = "반납이 최종 완료(COMPLETED)된 거래에 대해 거래 상대방에게 평점(1~5)과 후기를 남긴다. " +
            "거래당 본인 작성분은 1건으로 제한된다.",
        security = [SecurityRequirement(name = "JWT")],
    )
    fun createReview(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "거래(대여) ID", example = "1", required = true) rentalId: Long,
        @Valid request: RentalReviewCreateRequest,
    ): ResponseEntity<RentalReviewResponse>

    @Operation(
        summary = "거래에 달린 리뷰 조회",
        description = "해당 거래의 당사자만 조회할 수 있다. 대여자/등록자가 각각 남긴 리뷰가 있으면 최대 2건 반환된다.",
        security = [SecurityRequirement(name = "JWT")],
    )
    fun getReviewsForRental(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "거래(대여) ID", example = "1", required = true) rentalId: Long,
    ): ResponseEntity<List<RentalReviewResponse>>

    @Operation(
        summary = "사용자가 받은 리뷰 목록 조회",
        description = "offset이 아닌 cursor(keyset) 방식이다. 첫 요청은 cursor 없이 보내고, " +
            "응답의 nextCursor를 다음 요청의 cursor로 그대로 넘기면 이어서 조회된다.",
        security = [SecurityRequirement(name = "JWT")],
    )
    fun getReviewsForUser(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "리뷰 대상 사용자 ID", example = "1", required = true) userId: Long,
        @Parameter(description = "이전 응답의 nextCursor. 첫 요청이면 생략") cursor: String?,
        @Parameter(description = "페이지 크기") size: Int,
    ): ResponseEntity<CursorPageResponse<RentalReviewResponse>>

    @Operation(summary = "사용자 평균 평점 조회", security = [SecurityRequirement(name = "JWT")])
    fun getReviewStats(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "리뷰 대상 사용자 ID", example = "1", required = true) userId: Long,
    ): ResponseEntity<UserReviewStatsResponse>

    @Operation(
        summary = "사용자가 작성한 리뷰 목록 조회",
        description = "offset이 아닌 cursor(keyset) 방식이다. 첫 요청은 cursor 없이 보내고, " +
            "응답의 nextCursor를 다음 요청의 cursor로 그대로 넘기면 이어서 조회된다.",
        security = [SecurityRequirement(name = "JWT")],
    )
    fun getReviewsWrittenByUser(
        @Parameter(hidden = true) principal: CustomUserDetails,
        @Parameter(description = "리뷰 작성자 사용자 ID", example = "1", required = true) userId: Long,
        @Parameter(description = "이전 응답의 nextCursor. 첫 요청이면 생략") cursor: String?,
        @Parameter(description = "페이지 크기") size: Int,
    ): ResponseEntity<CursorPageResponse<RentalReviewResponse>>
}
