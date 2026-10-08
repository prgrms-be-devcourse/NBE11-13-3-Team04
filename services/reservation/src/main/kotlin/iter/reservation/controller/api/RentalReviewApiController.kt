package iter.reservation.controller.api

import iter.common.dto.response.CursorPageResponse
import iter.common.security.CustomUserDetails
import iter.reservation.controller.api.spec.RentalReviewApiSpec
import iter.reservation.dto.request.RentalReviewCreateRequest
import iter.reservation.dto.response.RentalReviewResponse
import iter.reservation.dto.response.UserReviewStatsResponse
import iter.reservation.service.RentalReviewService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('USER')")
class RentalReviewApiController(
    private val rentalReviewService: RentalReviewService,
) : RentalReviewApiSpec {

    @PostMapping("/rentals/{rentalId}/reviews")
    override fun createReview(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @Valid @RequestBody request: RentalReviewCreateRequest,
    ): ResponseEntity<RentalReviewResponse> {
        val reviewerId = principal.user.id
        return ResponseEntity.ok(rentalReviewService.createReview(reviewerId, rentalId, request))
    }

    @GetMapping("/rentals/{rentalId}/reviews")
    override fun getReviewsForRental(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
    ): ResponseEntity<List<RentalReviewResponse>> {
        val userId = principal.user.id
        return ResponseEntity.ok(rentalReviewService.getReviewsForRental(userId, rentalId))
    }

    @GetMapping("/users/{userId}/reviews")
    override fun getReviewsForUser(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("userId") userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<CursorPageResponse<RentalReviewResponse>> =
        ResponseEntity.ok(rentalReviewService.getReviewsForUser(userId, cursor, size))

    @GetMapping("/users/{userId}/reviews/stats")
    override fun getReviewStats(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("userId") userId: Long,
    ): ResponseEntity<UserReviewStatsResponse> =
        ResponseEntity.ok(rentalReviewService.getReviewStats(userId))

    @GetMapping("/users/{userId}/reviews/written")
    override fun getReviewsWrittenByUser(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("userId") userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<CursorPageResponse<RentalReviewResponse>> =
        ResponseEntity.ok(rentalReviewService.getReviewsWrittenByUser(userId, cursor, size))
}
