package com.example.iter.device.service

import com.example.iter.auth.api.UserQueryPort
import com.example.iter.auth.api.UserSummary
import com.example.iter.common.audit.domain.entity.AdminActionTargetType
import com.example.iter.common.audit.domain.entity.AdminActionType
import com.example.iter.common.audit.service.AdminActionService
import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import com.example.iter.device.domain.entity.Equipment
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.repository.EquipmentImageRepository
import com.example.iter.device.domain.repository.EquipmentRepository
import com.example.iter.device.dto.request.AdminEquipmentSearchRequest
import com.example.iter.device.dto.request.AdminEquipmentStatusRequest
import com.example.iter.device.dto.response.AdminEquipmentDetailResponse
import com.example.iter.device.dto.response.AdminEquipmentSummaryResponse
import com.example.iter.device.util.AdminEquipmentMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils

@Service
class AdminEquipmentService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentImageRepository: EquipmentImageRepository,
    private val userQueryPort: UserQueryPort,
    private val adminActionService: AdminActionService,
    private val adminEquipmentMapper: AdminEquipmentMapper
) {

    // 관리자가 장비명, 카테고리, 상태 조건으로 전체 장비 목록을 커서 조회합니다.
    @Transactional(readOnly = true)
    fun getEquipments(request: AdminEquipmentSearchRequest): CursorPageResponse<AdminEquipmentSummaryResponse> {
        val cursorKey = CursorCodec.decode(request.cursor)
        val equipment = equipmentRepository.searchForAdminByCursor(
            normalize(request.keyword),
            normalize(request.category),
            request.status,
            cursorKey?.createdAt,
            cursorKey?.id,
            PageRequest.of(0, request.size + 1)
        )
        val ownerMap = loadOwners(equipment)
        val thumbnailMap = loadThumbnails(equipment)

        return CursorPageResponse.from(
            equipment,
            request.size,
            { item ->
                adminEquipmentMapper.toSummary(
                    item,
                    getRequiredOwner(ownerMap, item.ownerId),
                    thumbnailMap[item.id]
                )
            }
        ) { item -> CursorKey(item.createdAt, item.id) }
    }

    // 관리자가 특정 장비의 상세 정보와 전체 이미지를 조회합니다.
    @Transactional(readOnly = true)
    fun getEquipmentDetail(equipmentId: Long): AdminEquipmentDetailResponse {
        val equipment = equipmentRepository.findById(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }
        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val images = equipmentImageRepository.findByEquipmentIdOrderBySortOrderAsc(equipmentId)

        return adminEquipmentMapper.toDetail(equipment, owner, images)
    }

    // 관리자가 장비를 차단 또는 차단 해제하고 관리자 조치 이력을 저장합니다.
    @Transactional
    fun updateEquipmentStatus(adminId: Long, equipmentId: Long, request: AdminEquipmentStatusRequest): AdminEquipmentDetailResponse {
        val equipment = equipmentRepository.findByIdForUpdate(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

        validateStatusChange(equipment, request.status)
        val action = applyStatus(equipment, request.status)

        adminActionService.record(
            adminId,
            AdminActionTargetType.EQUIPMENT,
            equipment.id,
            action,
            requireNotNull(request.reason).trim()
        )

        equipmentRepository.flush()
        log.info(
            "관리자 장비 상태 변경 처리: adminId={}, equipmentId={}, action={}, status={}",
            adminId,
            equipmentId,
            action,
            equipment.status
        )

        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val images = equipmentImageRepository.findByEquipmentIdOrderBySortOrderAsc(equipmentId)
        return adminEquipmentMapper.toDetail(equipment, owner, images)
    }

    private fun validateStatusChange(equipment: Equipment, requestedStatus: EquipmentStatus?) {
        val currentStatus = equipment.status
        val canSuspend = requestedStatus == EquipmentStatus.SUSPENDED &&
            (currentStatus == EquipmentStatus.ACTIVE || currentStatus == EquipmentStatus.INACTIVE)
        val canRestore = requestedStatus == EquipmentStatus.INACTIVE &&
            currentStatus == EquipmentStatus.SUSPENDED

        if (!canSuspend && !canRestore) {
            throw CustomException(ErrorCode.INVALID_EQUIPMENT_STATUS_TRANSITION)
        }
    }

    private fun applyStatus(equipment: Equipment, requestedStatus: EquipmentStatus?): AdminActionType = when (requestedStatus) {
        EquipmentStatus.SUSPENDED -> {
            equipment.changeStatus(EquipmentStatus.SUSPENDED)
            AdminActionType.SUSPEND_EQUIPMENT
        }
        EquipmentStatus.INACTIVE -> {
            equipment.changeStatus(EquipmentStatus.INACTIVE)
            AdminActionType.RESTORE_EQUIPMENT
        }
        else -> throw CustomException(ErrorCode.INVALID_EQUIPMENT_STATUS_TRANSITION)
    }

    private fun loadOwners(equipment: List<Equipment>): Map<Long, UserSummary> {
        if (equipment.isEmpty()) return emptyMap()
        return userQueryPort.findSummaries(equipment.map(Equipment::getOwnerId).distinct())
    }

    private fun loadThumbnails(equipment: List<Equipment>): Map<Long, String> {
        if (equipment.isEmpty()) return emptyMap()

        return buildMap {
            equipmentImageRepository
                .findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(equipment.map(Equipment::getId))
                .forEach { image -> putIfAbsent(image.equipment.id, image.imageUrl) }
        }
    }

    private fun getRequiredOwner(ownerMap: Map<Long, UserSummary>, ownerId: Long): UserSummary =
        ownerMap[ownerId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)

    private fun normalize(value: String?): String? = value
        ?.takeIf(StringUtils::hasText)
        ?.trim()

    private companion object {
        private val log = LoggerFactory.getLogger(AdminEquipmentService::class.java)
    }
}
