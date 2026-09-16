package com.example.iter.device.controller.api.spec

import com.example.iter.common.security.CustomUserDetails
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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity

@Tag(name = "Equipment Management", description = "장비 등록·수정·삭제·공개 상태 관리 API")
@SecurityRequirement(name = "JWT")
interface EquipmentManagementApiSpec {

    @Operation(
        summary = "장비 이미지 업로드 URL 발급",
        description = "S3에 이미지를 직접 업로드할 수 있는 5분 만료 Presigned PUT URL을 발급합니다.",
    )
    fun issueImageUploadUrls(
        @Parameter(hidden = true) principal: CustomUserDetails,
        request: PresignedImageUploadRequest,
    ): ResponseEntity<PresignedImageUploadResponse>

    @Operation(summary = "장비 등록", description = "Presigned URL로 업로드한 이미지 객체 키와 장비 정보를 등록합니다.")
    @ApiResponse(responseCode = "201", description = "장비 등록 성공")
    fun create(
        @Parameter(hidden = true) principal: CustomUserDetails,
        request: EquipmentCreateRequest,
    ): ResponseEntity<EquipmentDetailResponse>

    @Operation(summary = "장비 수정")
    fun update(
        @Parameter(hidden = true) principal: CustomUserDetails,
        equipmentId: Long,
        request: EquipmentUpdateRequest,
    ): ResponseEntity<EquipmentDetailResponse>

    @Operation(summary = "장비 삭제", description = "진행 중 거래와 분쟁이 없는 장비를 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "장비 삭제 성공")
    fun delete(
        @Parameter(hidden = true) principal: CustomUserDetails,
        equipmentId: Long,
    ): ResponseEntity<Void>

    @Operation(summary = "장비 공개 중지 또는 재개")
    fun updateStatus(
        @Parameter(hidden = true) principal: CustomUserDetails,
        equipmentId: Long,
        request: EquipmentStatusUpdateRequest,
    ): ResponseEntity<EquipmentStatusResponse>

    @Operation(
        summary = "장비 이미지 등록",
        description = "Presigned URL로 업로드한 이미지를 기존 장비에 추가합니다. " +
            "기존 이미지와 합쳐 최대 5장까지 등록할 수 있습니다.",
    )
    @ApiResponse(responseCode = "201", description = "장비 이미지 등록 성공")
    fun addImages(
        @Parameter(hidden = true) principal: CustomUserDetails,
        equipmentId: Long,
        request: EquipmentImageCreateRequest,
    ): ResponseEntity<List<EquipmentImageResponse>>

    @Operation(
        summary = "장비 이미지 삭제",
        description = "장비에는 최소 한 장의 이미지를 유지합니다. 대표 이미지를 삭제하면 다음 순서의 이미지가 대표 이미지가 됩니다.",
    )
    @ApiResponse(responseCode = "204", description = "장비 이미지 삭제 성공")
    fun deleteImage(
        @Parameter(hidden = true) principal: CustomUserDetails,
        equipmentId: Long,
        imageId: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "장비 예약 일정 조회",
        description = "장비 소유자 또는 관리자가 조회 기간과 겹치는 확정 예약 일정을 조회합니다.",
    )
    fun getSchedule(
        @Parameter(hidden = true) principal: CustomUserDetails,
        equipmentId: Long,
        @ParameterObject request: EquipmentScheduleRequest,
    ): ResponseEntity<EquipmentScheduleResponse>
}
