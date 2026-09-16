package com.example.iter.reservation.controller.api

import com.example.iter.reservation.controller.api.spec.RentalEvidenceUploadApiSpec
import com.example.iter.reservation.dto.request.EvidenceImagePresignRequest
import com.example.iter.reservation.dto.response.EvidenceImagePresignResponse
import com.example.iter.reservation.service.RentalEvidenceUploadService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rentals/images")
@PreAuthorize("hasRole('USER')")
class RentalEvidenceUploadApiController(
    private val rentalEvidenceUploadService: RentalEvidenceUploadService,
) : RentalEvidenceUploadApiSpec {

    @PostMapping("/presigned-urls")
    override fun createPresignedUploads(
        @RequestBody request: EvidenceImagePresignRequest,
    ): ResponseEntity<EvidenceImagePresignResponse> =
        ResponseEntity.ok(rentalEvidenceUploadService.createPresignedUploads(request))
}
