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
    private val adminEquipmentMapper: AdminEquipmentMapper,
) {

    // 관리자가 장비명, 카테고리, 상태 조건으로 전체 장비 목록을 커서 조회합니다.
    @Transactional(readOnly = true)
    fun getEquipments(request: AdminEquipmentSearchRequest): CursorPageResponse<AdminEquipmentSummaryResponse> {
        val keyword = normalize(request.keyword)
        val category = normalize(request.category)
        val cursorKey = CursorCodec.decode(request.cursor)

        val equipment = equipmentRepository.searchForAdminByCursor(
            keyword,
            category,
            request.status,
            cursorKey?.createdAt(),
            cursorKey?.id(),
            PageRequest.of(0, request.size + 1),
        )

        val ownerMap = loadOwners(equipment)
        val thumbnailMap = loadThumbnails(equipment)

        return CursorPageResponse.from(
            equipment,
            request.size,
            { item -> adminEquipmentMapper.toSummary(item, getRequiredOwner(ownerMap, item.ownerId), thumbnailMap[item.id!!]) },
            { item -> CursorKey(item.createdAt, item.id) },
        )
    }

    // 관리자가 특정 장비의 상세 정보와 전체 이미지를 조회합니다.
    //
    // equipmentId 를 Long? 으로 받는다. non-null Long 으로 두면 JVM 시그니처가 long 이 되어
    // AdminEquipmentApiControllerTest 의 verify(..., never()).getEquipmentDetail(any()) 처럼
    // Mockito 매처가 null 을 대신 넣는 호출에서 언박싱 NPE 가 난다 (Equipment.isOwnedBy 와 같은 이유).
    @Transactional(readOnly = true)
    fun getEquipmentDetail(equipmentId: Long?): AdminEquipmentDetailResponse {
        val equipment = equipmentRepository.findById(equipmentId!!)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }
        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val images = equipmentImageRepository.findByEquipmentIdOrderBySortOrderAsc(equipmentId)

        return adminEquipmentMapper.toDetail(equipment, owner, images)
    }

    // 관리자가 장비를 차단 또는 차단 해제하고 관리자 조치 이력을 저장합니다.
    //
    // adminId·equipmentId 를 Long? 으로 받는 이유는 getEquipmentDetail 의 주석과 같다 —
    // AdminEquipmentApiControllerTest 가 verify(..., never()).updateEquipmentStatus(any(), any(), any())
    // 로 셋을 전부 타입 없는 any() 로 넘긴다.
    @Transactional
    fun updateEquipmentStatus(
        adminId: Long?,
        equipmentId: Long?,
        request: AdminEquipmentStatusRequest,
    ): AdminEquipmentDetailResponse {
        val equipment = equipmentRepository.findByIdForUpdate(equipmentId!!)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

        validateStatusChange(equipment, request.status)
        val action = applyStatus(equipment, request.status)

        adminActionService.record(
            adminId,
            AdminActionTargetType.EQUIPMENT,
            equipment.id,
            action,
            request.reason!!.trim(),
        )

        equipmentRepository.flush()
        log.info(
            "관리자 장비 상태 변경 처리: adminId={}, equipmentId={}, action={}, status={}",
            adminId, equipmentId, action, equipment.status,
        )

        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val images = equipmentImageRepository.findByEquipmentIdOrderBySortOrderAsc(equipmentId)

        return adminEquipmentMapper.toDetail(equipment, owner, images)
    }

    // 현재 상태와 요청 상태가 관리자의 장비 차단 정책에 맞는지 검증합니다.
    private fun validateStatusChange(equipment: Equipment, requestedStatus: EquipmentStatus?) {
        val currentStatus = equipment.status

        // 관리자는 현재 장비 상태가 ACTIVE 또는 INACTIVE인 경우에만 장비 상태를 SUSPENDED로 변경하여 차단할 수 있습니다.
        val canSuspend = requestedStatus == EquipmentStatus.SUSPENDED &&
            (currentStatus == EquipmentStatus.ACTIVE || currentStatus == EquipmentStatus.INACTIVE)

        // 차단 해제 시 바로 대여 가능한 상태가 되지 않도록 INACTIVE로 복구합니다.
        // 이후 장비를 다시 활성화할지는 등록자가 직접 결정합니다.
        val canRestore = requestedStatus == EquipmentStatus.INACTIVE && currentStatus == EquipmentStatus.SUSPENDED

        if (!canSuspend && !canRestore) {
            throw CustomException(ErrorCode.INVALID_EQUIPMENT_STATUS_TRANSITION)
        }
    }

    // 장비 상태를 변경하고 저장할 관리자 조치 유형을 반환합니다.
    private fun applyStatus(equipment: Equipment, requestedStatus: EquipmentStatus?): AdminActionType {
        if (requestedStatus == EquipmentStatus.SUSPENDED) {
            equipment.changeStatus(EquipmentStatus.SUSPENDED)
            return AdminActionType.SUSPEND_EQUIPMENT
        }

        if (requestedStatus == EquipmentStatus.INACTIVE) {
            equipment.changeStatus(EquipmentStatus.INACTIVE)
            return AdminActionType.RESTORE_EQUIPMENT
        }

        throw CustomException(ErrorCode.INVALID_EQUIPMENT_STATUS_TRANSITION)
    }

    // 한 페이지에 포함된 장비 등록자를 한 번에 조회해 ID 기준 Map으로 변환합니다.
    private fun loadOwners(equipment: List<Equipment>): Map<Long, UserSummary> {
        if (equipment.isEmpty()) {
            return emptyMap()
        }

        val ownerIds = equipment.map { it.ownerId }.distinct()

        return userQueryPort.findSummaries(ownerIds)
    }

    // 한 페이지에 포함된 장비의 썸네일을 한 번에 조회해 장비 ID 기준 Map으로 변환합니다.
    // (first, ignored) -> first 머지 함수였다 — 정렬 순서상 첫 썸네일이 이긴다.
    // associateBy 는 나중 값이 이기므로, distinctBy 로 첫 등장만 남긴 뒤 associate 한다.
    private fun loadThumbnails(equipment: List<Equipment>): Map<Long, String> {
        if (equipment.isEmpty()) {
            return emptyMap()
        }

        val equipmentIds = equipment.map { it.id!! }

        return equipmentImageRepository
            .findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(equipmentIds)
            .distinctBy { it.equipment.id }
            .associate { it.equipment.id!! to it.imageUrl }
    }

    // 등록자 Map에서 회원을 찾고 데이터가 없으면 예외를 발생시킵니다.
    private fun getRequiredOwner(ownerMap: Map<Long, UserSummary>, ownerId: Long): UserSummary =
        ownerMap[ownerId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)

    // 검색 문자열의 앞뒤 공백을 제거하고 빈 문자열은 조회 조건에서 제외합니다.
    private fun normalize(value: String?): String? =
        if (StringUtils.hasText(value)) value!!.trim() else null

    companion object {
        private val log = LoggerFactory.getLogger(AdminEquipmentService::class.java)
    }
}
