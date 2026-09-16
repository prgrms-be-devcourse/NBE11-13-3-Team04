package com.example.iter.device.controller.api

import com.example.iter.common.security.CustomUserDetails
import com.example.iter.device.controller.api.spec.EquipmentManagementApiSpec
import com.example.iter.device.dto.request.EquipmentCreateRequest
import com.example.iter.device.dto.request.EquipmentImageCreateRequest
import com.example.iter.device.dto.request.EquipmentScheduleRequest
import com.example.iter.device.dto.request.EquipmentStatusUpdateRequest
import com.example.iter.device.dto.request.EquipmentUpdateRequest
import com.example.iter.device.dto.request.PresignedImageUploadRequest
import com.example.iter.device.dto.response.EquipmentDetailResponse
import com.example.iter.device.dto.response.EquipmentImageResponse
import com.example.iter.device.dto.response.EquipmentScheduleResponse
import com.example.iter.device.dto.response.EquipmentStatusResponse
import com.example.iter.device.dto.response.PresignedImageUploadResponse
import com.example.iter.device.service.EquipmentImageUploadService
import com.example.iter.device.service.EquipmentManagementService
import com.example.iter.device.service.EquipmentQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/devices")
class EquipmentManagementApiController(
    private val equipmentManagementService: EquipmentManagementService,
    private val equipmentImageUploadService: EquipmentImageUploadService,
    private val equipmentQueryService: EquipmentQueryService,
) : EquipmentManagementApiSpec {

    @PostMapping("/images/presigned-urls")
    override fun issueImageUploadUrls(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody request: PresignedImageUploadRequest,
    ): ResponseEntity<PresignedImageUploadResponse> =
        ResponseEntity.ok(equipmentImageUploadService.issue(principal.user, request))

    @PostMapping
    override fun create(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @Valid @RequestBody request: EquipmentCreateRequest,
    ): ResponseEntity<EquipmentDetailResponse> {
        val response = equipmentManagementService.create(principal.user, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{equipmentId}")
    override fun update(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("equipmentId") equipmentId: Long,
        @Valid @RequestBody request: EquipmentUpdateRequest,
    ): ResponseEntity<EquipmentDetailResponse> =
        ResponseEntity.ok(equipmentManagementService.update(principal.user.id, equipmentId, request))

    @DeleteMapping("/{equipmentId}")
    override fun delete(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("equipmentId") equipmentId: Long,
    ): ResponseEntity<Void> {
        equipmentManagementService.delete(principal.user.id, equipmentId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{equipmentId}/status")
    override fun updateStatus(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("equipmentId") equipmentId: Long,
        @Valid @RequestBody request: EquipmentStatusUpdateRequest,
    ): ResponseEntity<EquipmentStatusResponse> =
        ResponseEntity.ok(equipmentManagementService.updateStatus(principal.user, equipmentId, request))

    @PostMapping("/{equipmentId}/images")
    override fun addImages(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("equipmentId") equipmentId: Long,
        @Valid @RequestBody request: EquipmentImageCreateRequest,
    ): ResponseEntity<List<EquipmentImageResponse>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(equipmentManagementService.addImages(principal.user, equipmentId, request))

    @DeleteMapping("/{equipmentId}/images/{imageId}")
    override fun deleteImage(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("equipmentId") equipmentId: Long,
        @PathVariable("imageId") imageId: Long,
    ): ResponseEntity<Void> {
        equipmentManagementService.deleteImage(principal.user.id, equipmentId, imageId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{equipmentId}/rentals")
    override fun getSchedule(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable("equipmentId") equipmentId: Long,
        @Valid @ModelAttribute request: EquipmentScheduleRequest,
    ): ResponseEntity<EquipmentScheduleResponse> =
        ResponseEntity.ok(equipmentQueryService.getEquipmentSchedule(principal.user, equipmentId, request))
}
