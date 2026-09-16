package com.example.iter.reservation.controller.api;

import com.example.iter.common.security.CustomUserDetails;
import com.example.iter.reservation.controller.api.spec.RentalEvidenceUploadApiSpec;
import com.example.iter.reservation.dto.request.EvidenceImagePresignRequest;
import com.example.iter.reservation.dto.response.EvidenceImagePresignResponse;
import com.example.iter.reservation.service.RentalEvidenceUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/rentals/{rentalId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class RentalEvidenceUploadApiController implements RentalEvidenceUploadApiSpec {

    private final RentalEvidenceUploadService rentalEvidenceUploadService;

    @Override
    @PostMapping("/presigned-urls")
    public ResponseEntity<EvidenceImagePresignResponse> createPresignedUploads(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long rentalId,
            @RequestBody EvidenceImagePresignRequest request
    ) {
        return ResponseEntity.ok(rentalEvidenceUploadService.createPresignedUploads(
                principal.getUser().getId(), rentalId, request));
    }
}
