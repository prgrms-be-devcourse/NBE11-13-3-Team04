package iter.reservation.controller.api

import iter.common.dto.request.PagingRequest
import iter.common.dto.response.PageResponse
import iter.common.security.CustomUserDetails
import iter.reservation.controller.api.spec.ReturnApiSpec
import iter.reservation.dto.request.ReturnConfirmationRequest
import iter.reservation.dto.response.ReturnComparisonResponse
import iter.reservation.dto.response.ReturnConfirmationResponse
import iter.reservation.dto.response.ReturnTargetResponse
import iter.reservation.service.ReturnService
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
class ReturnApiController(
    private val returnService: ReturnService,
) : ReturnApiSpec {

    // 등록자가 확인해야 하는 반납 거래 목록을 조회합니다.
    @GetMapping("/returns")
    override fun getReturnTargets(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @ModelAttribute request: PagingRequest,
    ): ResponseEntity<PageResponse<ReturnTargetResponse>> {
        val ownerId = principal.user.id

        return ResponseEntity.ok(returnService.getReturnTargets(ownerId, request))
    }

    // 수령 당시 증빙과 반납 당시 증빙을 비교 조회합니다.
    @GetMapping("/{rentalId}/return-comparison")
    override fun getReturnComparison(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
    ): ResponseEntity<ReturnComparisonResponse> {
        val userId = principal.user.id

        return ResponseEntity.ok(returnService.getReturnComparison(userId, rentalId))
    }

    // 등록자가 반납을 정상 또는 비정상으로 최종 확인합니다.
    @PostMapping("/{rentalId}/return-confirmation")
    override fun confirmReturn(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @RequestBody request: ReturnConfirmationRequest,
    ): ResponseEntity<ReturnConfirmationResponse> {
        val ownerId = principal.user.id

        return ResponseEntity.ok(returnService.confirmReturn(ownerId, rentalId, request))
    }
}
