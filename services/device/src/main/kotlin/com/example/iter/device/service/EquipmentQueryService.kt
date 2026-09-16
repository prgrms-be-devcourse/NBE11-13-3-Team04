package com.example.iter.device.service

import com.example.iter.auth.api.UserQueryPort
import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.AuthUser
import com.example.iter.common.security.Role
import com.example.iter.device.domain.entity.Equipment
import com.example.iter.device.domain.entity.EquipmentStatus
import com.example.iter.device.domain.repository.EquipmentImageRepository
import com.example.iter.device.domain.repository.EquipmentRepository
import com.example.iter.device.dto.request.EquipmentAvailabilityRequest
import com.example.iter.device.dto.request.EquipmentEstimateRequest
import com.example.iter.device.dto.request.EquipmentScheduleRequest
import com.example.iter.device.dto.request.EquipmentSearchRequest
import com.example.iter.device.dto.request.EquipmentSort
import com.example.iter.device.dto.request.MyEquipmentSearchRequest
import com.example.iter.device.dto.response.AvailabilityReason
import com.example.iter.device.dto.response.EquipmentAvailabilityResponse
import com.example.iter.device.dto.response.EquipmentDetailResponse
import com.example.iter.device.dto.response.EquipmentEstimateResponse
import com.example.iter.device.dto.response.EquipmentImageResponse
import com.example.iter.device.dto.response.EquipmentListResponse
import com.example.iter.device.dto.response.EquipmentOwnerResponse
import com.example.iter.device.dto.response.EquipmentScheduleResponse
import com.example.iter.device.dto.response.EquipmentSummaryResponse
import com.example.iter.device.dto.response.MyEquipmentSummaryResponse
import com.example.iter.device.dto.response.RentalScheduleItemResponse
import com.example.iter.device.support.EquipmentImageUrlResolver
import com.example.iter.reservation.api.RentalQueryPort
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class EquipmentQueryService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentImageRepository: EquipmentImageRepository,
    private val userQueryPort: UserQueryPort,
    private val rentalQueryPort: RentalQueryPort,
    private val imageUrlResolver: EquipmentImageUrlResolver,
) {

    // startDate·endDate 는 @NotNull 로 컨트롤러 진입 전에 검증되므로, 이 시점에는 항상 값이 있다.
    fun getEquipmentAvailability(
        equipmentId: Long,
        request: EquipmentAvailabilityRequest,
    ): EquipmentAvailabilityResponse {
        val startDate = request.startDate!!
        val endDate = request.endDate!!
        val equipment = findPublicEquipment(equipmentId)
        val reason = findUnavailabilityReason(equipment, startDate, endDate)

        return EquipmentAvailabilityResponse(
            equipmentId,
            startDate,
            endDate,
            reason == null,
            reason,
        )
    }

    fun getEquipmentEstimate(
        equipmentId: Long,
        request: EquipmentEstimateRequest,
    ): EquipmentEstimateResponse {
        val startDate = request.startDate!!
        val endDate = request.endDate!!
        val equipment = findPublicEquipment(equipmentId)
        val reason = findUnavailabilityReason(equipment, startDate, endDate)
        if (reason != null) {
            throw CustomException(ErrorCode.EQUIPMENT_RENTAL_PERIOD_UNAVAILABLE)
        }

        val rentalDays = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1)
        val totalPrice = equipment.dailyPrice.multiply(BigDecimal.valueOf(rentalDays.toLong()))

        return EquipmentEstimateResponse(
            equipmentId,
            startDate,
            endDate,
            rentalDays,
            equipment.dailyPrice,
            totalPrice,
        )
    }

    // 공개(ACTIVE) 장비는 누구나 조회 가능하고, 그 외 상태(숨김/점검/차단/삭제)는 소유자 본인만 조회할 수 있다.
    fun getEquipmentDetail(requesterId: Long?, equipmentId: Long): EquipmentDetailResponse {
        val equipment = equipmentRepository.findById(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }
        val isOwner = requesterId != null && equipment.isOwnedBy(requesterId)
        if (equipment.status != EquipmentStatus.ACTIVE && !isOwner) {
            throw CustomException(ErrorCode.EQUIPMENT_NOT_FOUND)
        }
        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }
        val images = equipmentImageRepository
            .findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
            .map { image -> EquipmentImageResponse.from(image, imageUrlResolver.resolve(image)) }

        return EquipmentDetailResponse(
            equipment.id!!,
            equipment.name,
            equipment.category,
            equipment.description,
            equipment.dailyPrice,
            equipment.availableFrom,
            equipment.availableTo,
            equipment.status,
            equipment.productCondition,
            equipment.conditionDetail,
            images,
            EquipmentOwnerResponse(owner.userId, owner.nickName),
            0.0,
            0L,
            equipment.createdAt,
        )
    }

    private fun findPublicEquipment(equipmentId: Long): Equipment =
        equipmentRepository.findByIdAndStatus(equipmentId, EquipmentStatus.ACTIVE)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

    // 반환값이 null 이면 "대여 가능"을 뜻한다 — 사유가 없다는 것과 사유가 있다는 것을 구분해야 하므로
    // AvailabilityReason? 을 그대로 유지하고, Elvis 로 접어서 의미를 바꾸지 않는다.
    private fun findUnavailabilityReason(
        equipment: Equipment,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AvailabilityReason? {
        val availableFrom = equipment.availableFrom
        val availableTo = equipment.availableTo
        if (availableFrom == null ||
            availableTo == null ||
            startDate.isBefore(availableFrom) ||
            endDate.isAfter(availableTo)
        ) {
            return AvailabilityReason.OUT_OF_AVAILABLE_PERIOD
        }

        val conflict = rentalQueryPort.hasConflictingOccupyingRental(equipment.id!!, startDate, endDate)
        return if (conflict) AvailabilityReason.RESERVATION_CONFLICT else null
    }

    fun getEquipmentList(request: EquipmentSearchRequest): EquipmentListResponse {
        val equipmentPage = findPublicEquipment(request)

        // 저장된 엔티티만 여기로 들어오므로 id 는 항상 있다(AdminEquipmentMapper 의 근거와 같다).
        val equipmentIds = equipmentPage.content.map { it.id!! }
        val thumbnailUrls = findThumbnailUrls(equipmentIds)
        val content = equipmentPage.content.map { equipment ->
            toResponse(equipment, thumbnailUrls[equipment.id!!])
        }

        return EquipmentListResponse(
            content,
            equipmentPage.number,
            equipmentPage.size,
            equipmentPage.totalElements,
            equipmentPage.totalPages,
            equipmentPage.isFirst,
            equipmentPage.isLast,
        )
    }

    fun getMyEquipment(
        ownerId: Long,
        request: MyEquipmentSearchRequest,
    ): PageResponse<MyEquipmentSummaryResponse> {
        val equipmentPage = findMyEquipment(ownerId, request)
        val equipmentIds = equipmentPage.content.map { it.id!! }
        val thumbnailUrls = findThumbnailUrls(equipmentIds)
        val responsePage = equipmentPage.map { equipment ->
            MyEquipmentSummaryResponse(
                equipment.id!!,
                equipment.name,
                equipment.category,
                equipment.dailyPrice,
                equipment.status,
                equipment.productCondition,
                thumbnailUrls[equipment.id!!],
                equipment.availableFrom,
                equipment.availableTo,
            )
        }
        return PageResponse.from(responsePage)
    }

    fun getEquipmentSchedule(
        requester: AuthUser,
        equipmentId: Long,
        request: EquipmentScheduleRequest,
    ): EquipmentScheduleResponse {
        val equipment = equipmentRepository.findById(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND, "존재하지 않는 장비입니다.") }
        if (!equipment.isOwnedBy(requester.id) && requester.role != Role.ADMIN) {
            throw CustomException(ErrorCode.FORBIDDEN, "본인 소유 장비의 예약 일정만 조회할 수 있습니다.")
        }

        // 일정에 표시하지 않는 상태(취소·거절 등)는 reservation 이 걸러서 준다.
        val rentals = rentalQueryPort
            .findSchedule(equipmentId, request.from!!, request.to!!)
            .map { RentalScheduleItemResponse.from(it) }
        return EquipmentScheduleResponse(equipmentId, request.from, request.to, rentals)
    }

    private fun findThumbnailUrls(equipmentIds: List<Long>): Map<Long, String> {
        if (equipmentIds.isEmpty()) {
            return emptyMap()
        }

        val thumbnailUrls = LinkedHashMap<Long, String>()
        equipmentImageRepository
            .findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc(equipmentIds)
            .forEach { image -> thumbnailUrls.putIfAbsent(image.equipment.id!!, imageUrlResolver.resolve(image)) }
        return thumbnailUrls
    }

    private fun findPublicEquipment(request: EquipmentSearchRequest): Page<Equipment> {
        val keyword = escapeLikePattern(normalize(request.keyword))
        return equipmentRepository.searchPublicEquipment(
            keyword,
            request.category,
            request.minPrice,
            request.maxPrice,
            request.startDate,
            request.endDate,
            PageRequest.of(request.page, request.size, equipmentSort(request.sort)),
        )
    }

    private fun findMyEquipment(
        ownerId: Long,
        request: MyEquipmentSearchRequest,
    ): Page<Equipment> {
        val pageable: Pageable = PageRequest.of(request.page, request.size, equipmentSort(request.sort))
        return if (request.status == null) {
            equipmentRepository.findByOwnerId(ownerId, pageable)
        } else {
            equipmentRepository.findByOwnerIdAndStatus(ownerId, request.status, pageable)
        }
    }

    // 검색어의 앞뒤 공백을 제거하고 빈 문자열은 조회 조건에서 제외합니다.
    // 코틀린 data class 의 compact constructor 부재로 EquipmentSearchRequest 에서
    // 이 서비스로 옮겨온 정규화 로직이다.
    private fun normalize(value: String?): String? =
        if (StringUtils.hasText(value)) value!!.trim() else null

    private fun equipmentSort(sort: EquipmentSort): Sort = when (sort) {
        EquipmentSort.LATEST -> Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"),
        )
        EquipmentSort.PRICE_ASC -> Sort.by(
            Sort.Order.asc("dailyPrice"),
            Sort.Order.desc("id"),
        )
        EquipmentSort.PRICE_DESC -> Sort.by(
            Sort.Order.desc("dailyPrice"),
            Sort.Order.desc("id"),
        )
        // 리뷰 기능은 비활성화됐지만 기존 요청 계약을 유지하기 위해 최신순으로 처리합니다.
        EquipmentSort.RATING_DESC -> Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"),
        )
    }

    private fun toResponse(equipment: Equipment, thumbnailUrl: String?): EquipmentSummaryResponse =
        EquipmentSummaryResponse(
            equipment.id!!,
            equipment.name,
            equipment.category,
            equipment.dailyPrice,
            equipment.availableFrom,
            equipment.availableTo,
            equipment.productCondition,
            thumbnailUrl,
            0.0,
            0L,
        )

    // EquipmentRepository.searchPublicEquipment() 의 escape '\' 와 짝이다 — 한쪽만 고치면
    // 검색어의 % 와 _ 가 다시 와일드카드가 된다(% 를 넣으면 전체 조회가 되는 버그).
    private fun escapeLikePattern(keyword: String?): String? {
        if (keyword == null) {
            return null
        }
        return keyword
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
