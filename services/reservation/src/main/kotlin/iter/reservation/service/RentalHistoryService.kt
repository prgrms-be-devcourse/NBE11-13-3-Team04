package iter.reservation.service

import iter.auth.api.UserQueryPort
import iter.auth.api.UserSummary
import iter.common.dto.request.PagingRequest
import iter.common.dto.response.PageResponse
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.device.api.EquipmentInfo
import iter.device.api.EquipmentQueryPort
import iter.device.api.EquipmentThumbnailQueryPort
import iter.reservation.domain.entity.Rental
import iter.reservation.domain.repository.RentalHistoryRepository
import iter.reservation.domain.repository.spec.RentalSpecifications
import iter.reservation.dto.request.RentalHistorySearchRequest
import iter.reservation.dto.response.RentalHistoryResponse
import iter.reservation.util.RentalHistoryMapper
import iter.reservation.util.RentalOverduePolicy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDate

@Service
class RentalHistoryService(
    private val rentalHistoryRepository: RentalHistoryRepository,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val equipmentThumbnailQueryPort: EquipmentThumbnailQueryPort,
    private val userQueryPort: UserQueryPort,
    private val rentalHistoryMapper: RentalHistoryMapper,
) {

    // 로그인 사용자가 빌린 장비 이력을 조회합니다.
    //
    // renterId/ownerId는 전부 Long?로 받고 곧장 !!로 푼다 — non-null Long으로 두면 JVM
    // 시그니처가 primitive long이 되는데, RentalHistoryApiControllerTest(apps/monolith)가
    // 이 서비스를 @MockitoBean으로 목킹하며 verify(..., never()).getBorrowedHistory(any(), any())
    // 처럼 타입 지정 없는 any()를 쓴다. any()는 null을 반환하는 매처라 primitive 언박싱 시
    // NPE가 난다(device D7에서 겪은 것과 동일한 함정).
    @Transactional(readOnly = true)
    fun getBorrowedHistory(renterId: Long?, request: RentalHistorySearchRequest): PageResponse<RentalHistoryResponse> {
        val renterId = renterId!!
        val rentals = rentalHistoryRepository.findAll(
            RentalSpecifications.borrowedHistory(renterId, request.status(), normalizeKeyword(request.equipmentName())),
            historyPageable(request.page, request.size),
        )

        return toBorrowedHistoryResponse(rentals, LocalDate.now())
    }

    // 로그인 사용자가 빌려준 장비 이력을 조회합니다.
    @Transactional(readOnly = true)
    fun getLentHistory(ownerId: Long?, request: RentalHistorySearchRequest): PageResponse<RentalHistoryResponse> {
        val ownerId = ownerId!!
        val rentals = rentalHistoryRepository.findLentHistory(
            ownerId,
            request.status(),
            normalizeKeyword(request.equipmentName()),
            historyPageable(request.page, request.size),
        )

        return toLentHistoryResponse(rentals, LocalDate.now())
    }

    // 로그인 사용자가 빌린 장비 중 현재 연체 중인 거래를 조회합니다.
    @Transactional(readOnly = true)
    fun getBorrowedOverdueHistory(renterId: Long?, request: PagingRequest): PageResponse<RentalHistoryResponse> {
        val renterId = renterId!!
        val today = LocalDate.now()

        val rentals = rentalHistoryRepository.findByRenterIdAndEndDateBeforeAndStatusIn(
            renterId,
            today,
            RentalOverduePolicy.statuses(),
            overduePageable(request.page, request.size),
        )

        return toBorrowedHistoryResponse(rentals, today)
    }

    // 로그인 사용자가 빌려준 장비 중 현재 연체 중인 거래를 조회합니다.
    @Transactional(readOnly = true)
    fun getLentOverdueHistory(ownerId: Long?, request: PagingRequest): PageResponse<RentalHistoryResponse> {
        val ownerId = ownerId!!
        val today = LocalDate.now()

        val rentals = rentalHistoryRepository.findByOwnerIdSnapshotAndEndDateBeforeAndStatusIn(
            ownerId,
            today,
            RentalOverduePolicy.statuses(),
            overduePageable(request.page, request.size),
        )

        return toLentHistoryResponse(rentals, today)
    }

    // ============================================================

    private fun toBorrowedHistoryResponse(rentals: Page<Rental>, today: LocalDate): PageResponse<RentalHistoryResponse> {
        if (rentals.isEmpty) {
            return emptyResponse(rentals)
        }

        val equipmentIds = rentals.content.map { it.equipmentId }.toSet()
        val equipmentMap = equipmentQueryPort.findAll(equipmentIds)

        val ownerIds = rentals.content.map { getEquipment(equipmentMap, it.equipmentId).ownerId }.toSet()

        val userMap = userQueryPort.findSummaries(ownerIds)
        val thumbnailMap = loadThumbnails(equipmentIds)

        val responses = rentals.content.map { rental ->
            val equipment = getEquipment(equipmentMap, rental.equipmentId)
            val owner = getUser(userMap, equipment.ownerId)

            rentalHistoryMapper.toResponse(
                rental,
                owner,
                thumbnailMap[rental.equipmentId],
                RentalOverduePolicy.calculateDays(rental, today),
            )
        }

        return toPageResponse(rentals, responses)
    }

    private fun toLentHistoryResponse(rentals: Page<Rental>, today: LocalDate): PageResponse<RentalHistoryResponse> {
        if (rentals.isEmpty) {
            return emptyResponse(rentals)
        }

        val renterIds = rentals.content.map { it.renterId }.toSet()

        val userMap = userQueryPort.findSummaries(renterIds)
        val thumbnailMap = loadThumbnails(rentals.content.map { it.equipmentId }.toSet())

        val responses = rentals.content.map { rental ->
            rentalHistoryMapper.toResponse(
                rental,
                getUser(userMap, rental.renterId),
                thumbnailMap[rental.equipmentId],
                RentalOverduePolicy.calculateDays(rental, today),
            )
        }

        return toPageResponse(rentals, responses)
    }

    private fun loadThumbnails(equipmentIds: Collection<Long>): Map<Long, String> =
        equipmentThumbnailQueryPort.findThumbnailUrls(equipmentIds)

    private fun getEquipment(equipmentMap: Map<Long, EquipmentInfo>, equipmentId: Long): EquipmentInfo =
        equipmentMap[equipmentId] ?: throw CustomException(ErrorCode.EQUIPMENT_NOT_FOUND)

    private fun getUser(userMap: Map<Long, UserSummary>, userId: Long): UserSummary =
        userMap[userId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)

    private fun historyPageable(page: Int, size: Int): Pageable =
        PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")),
        )

    private fun overduePageable(page: Int, size: Int): Pageable =
        PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.ASC, "endDate")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                .and(Sort.by(Sort.Direction.DESC, "id")),
        )

    private fun normalizeKeyword(keyword: String?): String? {
        if (keyword == null || keyword.isBlank()) {
            return null
        }

        return keyword.trim()
    }

    private fun emptyResponse(rentals: Page<Rental>): PageResponse<RentalHistoryResponse> =
        PageResponse(
            emptyList(),
            rentals.number,
            rentals.size,
            rentals.totalElements,
            rentals.totalPages,
        )

    private fun toPageResponse(
        rentals: Page<Rental>,
        responses: List<RentalHistoryResponse>,
    ): PageResponse<RentalHistoryResponse> =
        PageResponse(
            responses,
            rentals.number,
            rentals.size,
            rentals.totalElements,
            rentals.totalPages,
        )
}
