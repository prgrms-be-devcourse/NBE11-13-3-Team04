package com.example.iter.device.controller.api

import com.example.iter.common.security.CustomUserDetails
import com.example.iter.device.controller.api.spec.EquipmentQueryApiSpec
import com.example.iter.device.dto.request.EquipmentAvailabilityRequest
import com.example.iter.device.dto.request.EquipmentEstimateRequest
import com.example.iter.device.dto.request.EquipmentSearchRequest
import com.example.iter.device.dto.response.EquipmentAvailabilityResponse
import com.example.iter.device.dto.response.EquipmentDetailResponse
import com.example.iter.device.dto.response.EquipmentEstimateResponse
import com.example.iter.device.dto.response.EquipmentListResponse
import com.example.iter.device.service.EquipmentQueryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/devices")
class EquipmentQueryApiController(
    private val equipmentQueryService: EquipmentQueryService,
) : EquipmentQueryApiSpec {

    @GetMapping("/{equipmentId}/availability")
    override fun getEquipmentAvailability(
        @PathVariable("equipmentId") equipmentId: Long,
        @Valid @ModelAttribute request: EquipmentAvailabilityRequest,
    ): ResponseEntity<EquipmentAvailabilityResponse> =
        ResponseEntity.ok(equipmentQueryService.getEquipmentAvailability(equipmentId, request))

    @GetMapping("/{equipmentId}/estimate")
    override fun getEquipmentEstimate(
        @PathVariable("equipmentId") equipmentId: Long,
        @Valid @ModelAttribute request: EquipmentEstimateRequest,
    ): ResponseEntity<EquipmentEstimateResponse> =
        ResponseEntity.ok(equipmentQueryService.getEquipmentEstimate(equipmentId, request))

    @GetMapping("/{equipmentId}")
    override fun getEquipmentDetail(
        @AuthenticationPrincipal principal: CustomUserDetails?,
        @PathVariable("equipmentId") equipmentId: Long,
    ): ResponseEntity<EquipmentDetailResponse> {
        val requesterId = principal?.user?.id
        return ResponseEntity.ok(equipmentQueryService.getEquipmentDetail(requesterId, equipmentId))
    }

    @GetMapping
    override fun getEquipmentList(
        @Valid @ModelAttribute request: EquipmentSearchRequest,
    ): ResponseEntity<EquipmentListResponse> =
        ResponseEntity.ok(equipmentQueryService.getEquipmentList(request))
}
