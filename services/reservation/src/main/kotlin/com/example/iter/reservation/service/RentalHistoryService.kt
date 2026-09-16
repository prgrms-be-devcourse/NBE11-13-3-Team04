package com.example.iter.reservation.service

import com.example.iter.auth.api.UserQueryPort
import com.example.iter.auth.api.UserSummary
import com.example.iter.common.dto.request.PagingRequest
import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.device.api.EquipmentThumbnailQueryPort
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.repository.RentalHistoryRepository
import com.example.iter.reservation.domain.repository.spec.RentalSpecifications
import com.example.iter.reservation.dto.request.RentalHistorySearchRequest
import com.example.iter.reservation.dto.response.RentalHistoryResponse
import com.example.iter.reservation.util.RentalHistoryMapper
import com.example.iter.reservation.util.RentalOverduePolicy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class RentalHistoryService(
    private val rentalHistoryRepository: RentalHistoryRepository,
    private val equipmentThumbnailQueryPort: EquipmentThumbnailQueryPort,
    private val userQueryPort: UserQueryPort,
    private val rentalHistoryMapper: RentalHistoryMapper,
    private val clock: Clock
) {

    // 대여자가 빌린 거래를 상태·장비명으로 검색하고 최신 거래부터 반환합니다.
    @Transactional(readOnly = true)
    fun getBorrowedHistory(renterId: Long, request: RentalHistorySearchRequest): PageResponse<RentalHistoryResponse> {
        val rentals = rentalHistoryRepository.findAll(
            RentalSpecifications.borrowedHistory(
                renterId,
                request.status,
                normalizeKeyword(request.equipmentName)
            ),
            historyPageable(request.page, request.size)
        )

        return toBorrowedHistoryResponse(rentals, LocalDate.now(clock))
    }

    // 현재 장비 소유자가 아니라 거래 생성 당시 등록자 스냅샷을 기준으로 빌려준 이력을 조회합니다.
    @Transactional(readOnly = true)
    fun getLentHistory(ownerId: Long, request: RentalHistorySearchRequest): PageResponse<RentalHistoryResponse> {
        val rentals = rentalHistoryRepository.findLentHistory(
            ownerId,
            request.status,
            normalizeKeyword(request.equipmentName),
            historyPageable(request.page, request.size)
        )

        return toLentHistoryResponse(rentals, LocalDate.now(clock))
    }

    // 종료일이 지났고 아직 반납이 끝나지 않은 대여자의 거래를 오래 연체된 순서로 조회합니다.
    @Transactional(readOnly = true)
    fun getBorrowedOverdueHistory(renterId: Long, request: PagingRequest): PageResponse<RentalHistoryResponse> {
        val today = LocalDate.now(clock)
        val rentals = rentalHistoryRepository.findByRenterIdAndEndDateBeforeAndStatusIn(
            renterId,
            today,
            RentalOverduePolicy.statuses(),
            overduePageable(request.page(), request.size())
        )

        return toBorrowedHistoryResponse(rentals, today)
    }

    // 거래 당시 등록자 스냅샷을 기준으로 빌려준 연체 이력을 조회합니다.
    @Transactional(readOnly = true)
    fun getLentOverdueHistory(ownerId: Long, request: PagingRequest): PageResponse<RentalHistoryResponse> {
        val today = LocalDate.now(clock)
        val rentals = rentalHistoryRepository.findByOwnerIdSnapshotAndEndDateBeforeAndStatusIn(
            ownerId,
            today,
            RentalOverduePolicy.statuses(),
            overduePageable(request.page(), request.size())
        )

        return toLentHistoryResponse(rentals, today)
    }

    // 현재 페이지의 등록자와 썸네일을 일괄 조회해 거래별 추가 조회가 발생하지 않게 합니다.
    private fun toBorrowedHistoryResponse(rentals: Page<Rental>, today: LocalDate): PageResponse<RentalHistoryResponse> {
        if (rentals.isEmpty) {
            return emptyResponse(rentals)
        }

        val equipmentIds = rentals.content.map(Rental::getEquipmentId).toSet()
        val ownerIds = rentals.content.map(Rental::getOwnerIdSnapshot).toSet()
        val userMap = userQueryPort.findSummaries(ownerIds)
        val thumbnailMap = loadThumbnails(equipmentIds)

        val responses = rentals.content.map { rental ->
            rentalHistoryMapper.toResponse(
                rental,
                getUser(userMap, rental.ownerIdSnapshot),
                thumbnailMap[rental.equipmentId],
                RentalOverduePolicy.calculateDays(rental, today)
            )
        }

        return toPageResponse(rentals, responses)
    }

    // 현재 페이지의 대여자와 썸네일을 일괄 조회해 거래별 추가 조회가 발생하지 않게 합니다.
    private fun toLentHistoryResponse(rentals: Page<Rental>, today: LocalDate): PageResponse<RentalHistoryResponse> {
        if (rentals.isEmpty) {
            return emptyResponse(rentals)
        }

        val renterIds = rentals.content.map(Rental::getRenterId).toSet()
        val equipmentIds = rentals.content.map(Rental::getEquipmentId).toSet()
        val userMap = userQueryPort.findSummaries(renterIds)
        val thumbnailMap = loadThumbnails(equipmentIds)

        val responses = rentals.content.map { rental ->
            rentalHistoryMapper.toResponse(
                rental,
                getUser(userMap, rental.renterId),
                thumbnailMap[rental.equipmentId],
                RentalOverduePolicy.calculateDays(rental, today)
            )
        }

        return toPageResponse(rentals, responses)
    }

    private fun loadThumbnails(equipmentIds: Collection<Long>): Map<Long, String> =
        equipmentThumbnailQueryPort.findThumbnailUrls(equipmentIds)

    private fun getUser(userMap: Map<Long, UserSummary>, userId: Long): UserSummary =
        userMap[userId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)

    private fun historyPageable(page: Int, size: Int): Pageable = PageRequest.of(
        page,
        size,
        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
    )

    // 연체 이력은 가장 오래 지난 종료일을 먼저 보여주고 동일 종료일에서는 최신 거래를 우선합니다.
    private fun overduePageable(page: Int, size: Int): Pageable = PageRequest.of(
        page,
        size,
        Sort.by(Sort.Direction.ASC, "endDate")
            .and(Sort.by(Sort.Direction.DESC, "createdAt"))
            .and(Sort.by(Sort.Direction.DESC, "id"))
    )

    private fun normalizeKeyword(keyword: String?): String? = keyword?.trim()?.takeIf(String::isNotEmpty)

    private fun emptyResponse(rentals: Page<Rental>): PageResponse<RentalHistoryResponse> = PageResponse(
        emptyList(),
        rentals.number,
        rentals.size,
        rentals.totalElements,
        rentals.totalPages
    )

    private fun toPageResponse(rentals: Page<Rental>, responses: List<RentalHistoryResponse>): PageResponse<RentalHistoryResponse> = PageResponse(
        responses,
        rentals.number,
        rentals.size,
        rentals.totalElements,
        rentals.totalPages
    )
}
