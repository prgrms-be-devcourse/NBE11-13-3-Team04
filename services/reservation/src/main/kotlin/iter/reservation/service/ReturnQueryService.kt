package iter.reservation.service

import iter.auth.api.UserQueryPort
import iter.auth.api.UserSummary
import iter.common.dto.request.PagingRequest
import iter.common.dto.response.PageResponse
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.common.image.CaptureView
import iter.device.api.EquipmentImageInfo
import iter.device.api.EquipmentImageQueryPort
import iter.device.api.EquipmentThumbnailQueryPort
import iter.reservation.api.RentalStatus
import iter.reservation.domain.entity.Rental
import iter.reservation.domain.entity.Receipt
import iter.reservation.domain.entity.ReturnReceipt
import iter.reservation.domain.repository.ReceiptImageRepository
import iter.reservation.domain.repository.ReceiptRepository
import iter.reservation.domain.repository.RentalRepository
import iter.reservation.domain.repository.ReturnReceiptImageRepository
import iter.reservation.domain.repository.ReturnReceiptRepository
import iter.reservation.dto.response.ConditionEvidenceImageResponse
import iter.reservation.dto.response.ReturnComparisonResponse
import iter.reservation.dto.response.ReturnTargetResponse
import iter.reservation.util.ReturnMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReturnQueryService(
    private val rentalRepository: RentalRepository,
    private val equipmentThumbnailQueryPort: EquipmentThumbnailQueryPort,
    private val equipmentImageQueryPort: EquipmentImageQueryPort,
    private val userQueryPort: UserQueryPort,
    private val receiptRepository: ReceiptRepository,
    private val receiptImageRepository: ReceiptImageRepository,
    private val returnReceiptRepository: ReturnReceiptRepository,
    private val returnReceiptImageRepository: ReturnReceiptImageRepository,
    private val returnMapper: ReturnMapper,
    private val evidenceUploadService: RentalEvidenceUploadService
) {
    // 거래 당시 등록자 스냅샷을 기준으로 최종 반납 확인이 필요한 거래만 조회합니다.
    @Transactional(readOnly = true)
    fun getReturnTargets(ownerId: Long, request: PagingRequest): PageResponse<ReturnTargetResponse> {
        val rentals = rentalRepository.findByOwnerIdSnapshotAndStatus(
            ownerId,
            RentalStatus.RETURNED,
            createPageRequest(request)
        )

        if (rentals.isEmpty) {
            return createEmptyPageResponse(rentals)
        }

        // 현재 페이지에 필요한 대여자·반납 증빙·썸네일을 일괄 조회해 N+1 쿼리를 방지합니다.
        val data = loadReturnTargetData(rentals.content)

        return PageResponse.from(rentals.map { rental -> toReturnTargetResponse(rental, data) })
    }

    // 거래 당사자에게 등록·수령·반납 증빙을 같은 촬영 방향 기준으로 제공합니다.
    @Transactional(readOnly = true)
    fun getReturnComparison(userId: Long, rentalId: Long): ReturnComparisonResponse {
        val rental = findRental(rentalId)
        validateParty(userId, rental)

        val renter = findUser(rental.renterId)
        val receipt = findReceipt(rentalId)
        val returnReceipt = findReturnReceipt(rentalId)

        return returnMapper.toComparison(
            rental,
            renter,
            findListingImages(rental.equipmentId),
            receipt,
            findReceiptImages(receipt.id!!),
            returnReceipt,
            findReturnReceiptImages(returnReceipt.id!!)
        )
    }

    private fun createPageRequest(request: PagingRequest): PageRequest = PageRequest.of(
        request.page,
        request.size,
        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
    )

    private fun createEmptyPageResponse(rentals: Page<Rental>): PageResponse<ReturnTargetResponse> = PageResponse(
        emptyList(),
        rentals.number,
        rentals.size,
        rentals.totalElements,
        rentals.totalPages
    )

    private fun loadReturnTargetData(rentals: List<Rental>): ReturnTargetData {
        val rentalIds = rentals.map { it.id!! }.toSet()
        val renterIds = rentals.map { it.renterId }.toSet()
        val equipmentIds = rentals.map { it.equipmentId }.toSet()

        return ReturnTargetData(
            userQueryPort.findSummaries(renterIds),
            findReturnReceiptsByRentalId(rentalIds),
            equipmentThumbnailQueryPort.findThumbnailUrls(equipmentIds)
        )
    }

    private fun findReturnReceiptsByRentalId(rentalIds: Set<Long>): Map<Long, ReturnReceipt> =
        returnReceiptRepository.findAllByRental_IdIn(rentalIds).associateBy { it.rental.id!! }

    private fun toReturnTargetResponse(rental: Rental, data: ReturnTargetData): ReturnTargetResponse {
        val renter = data.rentersById[rental.renterId] ?: throw CustomException(ErrorCode.USER_NOT_FOUND)
        val returnReceipt = data.returnReceiptsByRentalId[rental.id]
            ?: throw CustomException(ErrorCode.RETURN_RECEIPT_NOT_FOUND)

        return returnMapper.toTarget(
            rental,
            renter,
            data.thumbnailsByEquipmentId[rental.equipmentId],
            returnReceipt
        )
    }

    private fun findRental(rentalId: Long): Rental =
        rentalRepository.findById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    private fun findUser(userId: Long): UserSummary =
        userQueryPort.findSummary(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

    private fun findReceipt(rentalId: Long): Receipt =
        receiptRepository.findByRentalId(rentalId).orElseThrow { CustomException(ErrorCode.RECEIPT_NOT_FOUND) }

    private fun findReturnReceipt(rentalId: Long): ReturnReceipt =
        returnReceiptRepository.findByRentalId(rentalId).orElseThrow { CustomException(ErrorCode.RETURN_RECEIPT_NOT_FOUND) }

    private fun findListingImages(equipmentId: Long): List<ConditionEvidenceImageResponse> =
        equipmentImageQueryPort.findAll(equipmentId).map(::toConditionImage)

    private fun findReceiptImages(receiptId: Long): List<ConditionEvidenceImageResponse> =
        receiptImageRepository.findByReceipt_IdOrderBySortOrderAscIdAsc(receiptId).map { image ->
            ConditionEvidenceImageResponse(
                image.captureView,
                evidenceUploadService.createReadUrl(image.imageUrl)
            )
        }

    private fun findReturnReceiptImages(returnReceiptId: Long): List<ConditionEvidenceImageResponse> =
        returnReceiptImageRepository.findByReturnReceipt_IdOrderBySortOrderAscIdAsc(returnReceiptId).map { image ->
            ConditionEvidenceImageResponse(
                image.captureView,
                evidenceUploadService.createReadUrl(image.imageUrl)
            )
        }

    private fun toConditionImage(image: EquipmentImageInfo): ConditionEvidenceImageResponse =
        ConditionEvidenceImageResponse(
            image.captureView?.let(CaptureView::valueOf),
            image.imageUrl
        )

    // 현재 장비 소유자가 바뀌어도 거래 생성 당시 등록자와 대여자만 비교 화면에 접근할 수 있습니다.
    private fun validateParty(userId: Long, rental: Rental) {
        if (!rental.isRenter(userId) && rental.ownerIdSnapshot != userId) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    private data class ReturnTargetData(
        val rentersById: Map<Long, UserSummary>,
        val returnReceiptsByRentalId: Map<Long, ReturnReceipt>,
        val thumbnailsByEquipmentId: Map<Long, String>
    )
}
