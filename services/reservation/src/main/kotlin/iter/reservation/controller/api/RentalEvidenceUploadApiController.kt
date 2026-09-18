package iter.reservation.controller.api

import iter.common.security.CustomUserDetails
import iter.reservation.controller.api.spec.RentalEvidenceUploadApiSpec
import iter.reservation.dto.request.EvidenceImagePresignRequest
import iter.reservation.dto.response.EvidenceImagePresignResponse
import iter.reservation.service.RentalEvidenceUploadService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/rentals/{rentalId}/images")
@PreAuthorize("hasRole('USER')")
class RentalEvidenceUploadApiController(
    private val rentalEvidenceUploadService: RentalEvidenceUploadService,
) : RentalEvidenceUploadApiSpec {

    @PostMapping("/presigned-urls")
    override fun createPresignedUploads(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("rentalId") rentalId: Long,
        @RequestBody request: EvidenceImagePresignRequest,
    ): ResponseEntity<EvidenceImagePresignResponse> =
        ResponseEntity.ok(
            rentalEvidenceUploadService.createPresignedUploads(principal.user.id, rentalId, request),
        )
}
