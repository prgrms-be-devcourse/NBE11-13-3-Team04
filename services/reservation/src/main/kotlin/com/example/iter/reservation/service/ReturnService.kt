package com.example.iter.reservation.service

import com.example.iter.auth.api.UserQueryPort
import com.example.iter.auth.api.UserSummary
import com.example.iter.common.dto.request.PagingRequest
import com.example.iter.common.dto.response.PageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.device.api.EquipmentInfo
import com.example.iter.device.api.EquipmentOccupancyCommandPort
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.device.api.EquipmentThumbnailQueryPort
import com.example.iter.dispute.api.DisputeCommandPort
import com.example.iter.dispute.api.ReturnDisputeCommand
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.entity.ReceiptImage
import com.example.iter.reservation.domain.entity.Receipt
import com.example.iter.reservation.domain.entity.ReturnReceipt
import com.example.iter.reservation.domain.entity.ReturnReceiptImage
import com.example.iter.reservation.domain.repository.ReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReceiptRepository
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptRepository
import com.example.iter.reservation.dto.request.ReturnConfirmationRequest
import com.example.iter.reservation.dto.response.ReturnComparisonResponse
import com.example.iter.reservation.dto.response.ReturnConfirmationResponse
import com.example.iter.reservation.dto.response.ReturnTargetResponse
import com.example.iter.reservation.util.ReturnMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// apps/monolith의 ReturnApiControllerTest가 @MockitoBean으로 이 서비스를 목킹하며
// verify(mock, never()).confirmReturn(any(), any(), any()) 처럼 타입 지정 없는 any()를 쓴다.
// non-null Long으로 두면 JVM 시그니처가 primitive long이 되어 언박싱 NPE가 나므로(device D7,
// R6 RentalHistoryService와 동일한 함정) ownerId/userId/rentalId는 전부 Long?로 받고 메서드
// 본문 진입 직후 !!로 푼다.
@Service
class ReturnService(
    private val rentalRepository: RentalRepository,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val equipmentThumbnailQueryPort: EquipmentThumbnailQueryPort,
    private val equipmentOccupancyCommandPort: EquipmentOccupancyCommandPort,
    private val userQueryPort: UserQueryPort,
    private val receiptRepository: ReceiptRepository,
    private val receiptImageRepository: ReceiptImageRepository,
    private val returnReceiptRepository: ReturnReceiptRepository,
    private val returnReceiptImageRepository: ReturnReceiptImageRepository,
    private val disputeCommandPort: DisputeCommandPort,
    private val returnMapper: ReturnMapper,
) {

    // 등록자가 최종 반납 확인을 해야 하는 거래 목록을 조회합니다.
    // 필요한 회원·반납 증빙·썸네일을 일괄 결합해 페이징 응답으로 반환
    @Transactional(readOnly = true)
    fun getReturnTargets(ownerId: Long?, request: PagingRequest): PageResponse<ReturnTargetResponse> {
        val ownerId = ownerId!!
        val pageable = createPageRequest(request)

        val rentals = rentalRepository.findByOwnerIdSnapshotAndStatus(ownerId, RentalStatus.RETURNED, pageable)

        if (rentals.isEmpty) {
            return createEmptyPageResponse(rentals)
        }

        val data = loadReturnTargetData(rentals.content)
        val responses = rentals.map { toReturnTargetResponse(it, data) }

        return PageResponse.from(responses)
    }

    // 거래 당사자가 수령·반납 증빙을 비교 조회합니다.
    @Transactional(readOnly = true)
    fun getReturnComparison(userId: Long?, rentalId: Long?): ReturnComparisonResponse {
        val userId = userId!!
        val rentalId = rentalId!!
        val rental = findRental(rentalId)
        val equipment = findEquipment(rental.equipmentId)

        validateParty(userId, rental, equipment)

        val renter = findUser(rental.renterId)
        val receipt = findReceipt(rentalId)
        val returnReceipt = findReturnReceipt(rentalId)

        val receiptImageUrls = findReceiptImageUrls(receipt.id!!)
        val returnImageUrls = findReturnReceiptImageUrls(returnReceipt.id!!)

        return returnMapper.toComparison(rental, renter, receipt, receiptImageUrls, returnReceipt, returnImageUrls)
    }

    // 등록자가 반납을 정상 또는 비정상으로 최종 확인합니다.
    @Transactional
    fun confirmReturn(ownerId: Long?, rentalId: Long?, request: ReturnConfirmationRequest): ReturnConfirmationResponse {
        val ownerId = ownerId!!
        val rentalId = rentalId!!
        val rental = findRentalWithLock(rentalId)
        val equipment = findEquipment(rental.equipmentId)

        validateOwner(ownerId, equipment)
        validateConfirmationStatus(rental)
        validateEvidenceExists(rentalId)

        if (request.hasIssue() == false) {
            rental.completeReturn()
            equipmentOccupancyCommandPort.markVacated(rental.id)
            log.info(
                "대여 반납 확인 처리: rentalId={}, ownerId={}, status={}, hasIssue={}",
                rentalId, ownerId, rental.status, false,
            )

            return returnMapper.toConfirmation(rental, null)
        }

        val disputeId = createReturnDispute(rental, ownerId, request)

        rental.openReturnDispute()
        log.info(
            "대여 반납 분쟁 전환 처리: rentalId={}, ownerId={}, disputeId={}, status={}",
            rentalId, ownerId, disputeId, rental.status,
        )

        // 장비 상태는 변경하지 않습니다.
        // 장비 등록자가 이후 장비 관리 기능에서 직접 결정합니다.

        return returnMapper.toConfirmation(rental, disputeId)
    }

    // ===================================================

    // 반납 확인 대상 목록의 페이징과 정렬 조건을 생성합니다.
    private fun createPageRequest(request: PagingRequest): PageRequest =
        PageRequest.of(
            request.page(),
            request.size(),
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")),
        )

    // 조회 결과가 없을 때 빈 페이징 응답을 생성합니다.
    private fun createEmptyPageResponse(rentals: Page<Rental>): PageResponse<ReturnTargetResponse> =
        PageResponse(emptyList(), rentals.number, rentals.size, rentals.totalElements, rentals.totalPages)

    // 반납 확인 대상 응답에 필요한 회원·반납 증빙·썸네일을 일괄 조회합니다.
    private fun loadReturnTargetData(rentals: List<Rental>): ReturnTargetData {
        val rentalIds = rentals.map { it.id!! }.toSet()
        val renterIds = rentals.map { it.renterId }.toSet()
        val equipmentIds = rentals.map { it.equipmentId }.toSet()

        return ReturnTargetData(
            findRentersById(renterIds),
            findReturnReceiptsByRentalId(rentalIds),
            findThumbnailsByEquipmentId(equipmentIds),
        )
    }

    // 대여자 정보를 ID 기준으로 일괄 조회합니다.
    private fun findRentersById(renterIds: Set<Long>): Map<Long, UserSummary> =
        userQueryPort.findSummaries(renterIds)

    // 반납 증빙을 거래 ID 기준으로 일괄 조회합니다.
    private fun findReturnReceiptsByRentalId(rentalIds: Set<Long>): Map<Long, ReturnReceipt> =
        returnReceiptRepository.findAllByRental_IdIn(rentalIds).associateBy { it.rental.id!! }

    // 장비 썸네일을 장비 ID 기준으로 일괄 조회합니다.
    private fun findThumbnailsByEquipmentId(equipmentIds: Set<Long>): Map<Long, String> =
        equipmentThumbnailQueryPort.findThumbnailUrls(equipmentIds)

    // 거래와 일괄 조회한 데이터를 반납 확인 대상 응답으로 변환합니다.
    private fun toReturnTargetResponse(rental: Rental, data: ReturnTargetData): ReturnTargetResponse {
        val renter = data.rentersById[rental.renterId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)
        val returnReceipt = data.returnReceiptsByRentalId[rental.id]
            ?: throw CustomException(ErrorCode.RETURN_RECEIPT_NOT_FOUND)

        return returnMapper.toTarget(rental, renter, data.thumbnailsByEquipmentId[rental.equipmentId], returnReceipt)
    }

    // 거래를 조회합니다.
    private fun findRental(rentalId: Long): Rental =
        rentalRepository.findById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    // 반납 최종 확인을 위해 거래를 비관적 쓰기 락으로 조회합니다.
    private fun findRentalWithLock(rentalId: Long): Rental =
        rentalRepository.findWithLockById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    // 장비를 조회합니다.
    private fun findEquipment(equipmentId: Long): EquipmentInfo =
        equipmentQueryPort.find(equipmentId).orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

    // 회원을 조회합니다.
    private fun findUser(userId: Long): UserSummary =
        userQueryPort.findSummary(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

    // 수령 증빙을 조회합니다.
    private fun findReceipt(rentalId: Long): Receipt =
        receiptRepository.findByRentalId(rentalId).orElseThrow { CustomException(ErrorCode.RECEIPT_NOT_FOUND) }

    // 반납 증빙을 조회합니다.
    private fun findReturnReceipt(rentalId: Long): ReturnReceipt =
        returnReceiptRepository.findByRentalId(rentalId)
            .orElseThrow { CustomException(ErrorCode.RETURN_RECEIPT_NOT_FOUND) }

    // 수령 증빙 이미지 URL을 등록 순서대로 조회합니다.
    private fun findReceiptImageUrls(receiptId: Long): List<String> =
        receiptImageRepository.findByReceipt_IdOrderBySortOrderAscIdAsc(receiptId).map { it.imageUrl }

    // 반납 증빙 이미지 URL을 등록 순서대로 조회합니다.
    private fun findReturnReceiptImageUrls(returnReceiptId: Long): List<String> =
        returnReceiptImageRepository.findByReturnReceipt_IdOrderBySortOrderAscIdAsc(returnReceiptId)
            .map { it.imageUrl }

    // 반납 최종 확인에 필요한 수령·반납 증빙이 존재하는지 검사합니다.
    private fun validateEvidenceExists(rentalId: Long) {
        findReceipt(rentalId)
        findReturnReceipt(rentalId)
    }

    // 비정상 반납에 대한 최소 분쟁을 생성합니다.
    private fun createReturnDispute(rental: Rental, ownerId: Long, request: ReturnConfirmationRequest): Long {
        // 엔티티 조립은 dispute 가 한다. 여기서는 원시값만 넘긴다.
        return disputeCommandPort.openReturnDispute(
            ReturnDisputeCommand(
                rental.id,
                ownerId,
                rental.renterId,
                request.disputeReason()!!.trim(),
                request.disputeDescription()!!.trim(),
            )
        )
    }

    private fun validateParty(userId: Long, rental: Rental, equipment: EquipmentInfo) {
        val renter = rental.isRenter(userId)
        val owner = equipment.isOwnedBy(userId)

        if (!renter && !owner) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    // 로그인 사용자가 장비 등록자인지 확인합니다.
    private fun validateOwner(ownerId: Long, equipment: EquipmentInfo) {
        if (!equipment.isOwnedBy(ownerId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    // 반납 최종 확인이 가능한 거래 상태인지 검사합니다.
    private fun validateConfirmationStatus(rental: Rental) {
        if (rental.status == RentalStatus.COMPLETED || rental.status == RentalStatus.DISPUTED) {
            throw CustomException(ErrorCode.RETURN_ALREADY_CONFIRMED)
        }

        if (rental.status != RentalStatus.RETURNED) {
            throw CustomException(ErrorCode.INVALID_RETURN_CONFIRMATION_STATUS)
        }
    }

    // 반납 확인 대상 목록 응답 생성에 필요한 일괄 조회 결과입니다.
    private data class ReturnTargetData(
        val rentersById: Map<Long, UserSummary>,
        val returnReceiptsByRentalId: Map<Long, ReturnReceipt>,
        val thumbnailsByEquipmentId: Map<Long, String>,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ReturnService::class.java)
    }
}
