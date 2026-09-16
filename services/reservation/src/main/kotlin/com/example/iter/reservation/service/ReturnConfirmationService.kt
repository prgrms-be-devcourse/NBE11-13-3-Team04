package com.example.iter.reservation.service

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.device.api.EquipmentOccupancyCommandPort
import com.example.iter.dispute.api.DisputeCommandPort
import com.example.iter.dispute.api.ReturnDisputeCommand
import com.example.iter.dispute.api.ReturnDisputeResult
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.repository.ReceiptRepository
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptRepository
import com.example.iter.reservation.dto.request.ReturnConfirmationRequest
import com.example.iter.reservation.dto.response.ReturnConfirmationResponse
import com.example.iter.reservation.util.ReturnMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReturnConfirmationService(
    private val rentalRepository: RentalRepository,
    private val equipmentOccupancyCommandPort: EquipmentOccupancyCommandPort,
    private val receiptRepository: ReceiptRepository,
    private val returnReceiptRepository: ReturnReceiptRepository,
    private val disputeCommandPort: DisputeCommandPort,
    private val returnMapper: ReturnMapper
) {
    // 거래 행을 잠근 뒤 증빙과 상태를 재검증하고 정상 완료 또는 분쟁 전환을 원자적으로 처리합니다.
    @Transactional
    fun confirmReturn(ownerId: Long, rentalId: Long, request: ReturnConfirmationRequest): ReturnConfirmationResponse {
        val rental = findRentalWithLock(rentalId)

        validateOwner(ownerId, rental)
        validateConfirmationStatus(rental)
        validateEvidenceExists(rentalId)

        // 이상이 없을 때만 거래와 장비 점유를 종료합니다. 이상 반납은 등록자의 분쟁 판단을 보존합니다.
        if (request.hasIssue == false) {
            rental.completeReturn()
            equipmentOccupancyCommandPort.markVacated(rental.id!!)
            log.info(
                "대여 반납 확인 처리: rentalId={}, ownerId={}, status={}, hasIssue={}",
                rentalId,
                ownerId,
                rental.status,
                false
            )

            return returnMapper.toConfirmation(rental, null, null)
        }

        // 이상이 있으면 분쟁·신고를 생성하고 거래는 완료하지 않은 채 DISPUTED 상태로 전환합니다.
        val result = createReturnDispute(rental, ownerId, request)
        rental.openReturnDispute()
        log.info(
            "대여 반납 분쟁 전환 처리: rentalId={}, ownerId={}, disputeId={}, status={}",
            rentalId,
            ownerId,
            result.disputeId,
            rental.status
        )

        return returnMapper.toConfirmation(rental, result.disputeId, result.reportId)
    }

    private fun findRentalWithLock(rentalId: Long): Rental =
        rentalRepository.findWithLockById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    // 수령·반납 양쪽 증빙이 모두 있어야 등록자가 최종 상태를 비교해 확정할 수 있습니다.
    private fun validateEvidenceExists(rentalId: Long) {
        receiptRepository.findByRentalId(rentalId).orElseThrow { CustomException(ErrorCode.RECEIPT_NOT_FOUND) }
        returnReceiptRepository.findByRentalId(rentalId).orElseThrow { CustomException(ErrorCode.RETURN_RECEIPT_NOT_FOUND) }
    }

    private fun createReturnDispute(
        rental: Rental,
        ownerId: Long,
        request: ReturnConfirmationRequest
    ): ReturnDisputeResult = disputeCommandPort.openReturnDispute(
        ReturnDisputeCommand(
            rental.id!!,
            ownerId,
            rental.renterId,
            requireNotNull(request.disputeReason).trim(),
            requireNotNull(request.disputeDescription).trim()
        )
    )

    private fun validateOwner(ownerId: Long, rental: Rental) {
        if (rental.ownerIdSnapshot != ownerId) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    // RETURNED 상태에서 한 번만 처리하도록 완료·분쟁 상태의 중복 요청을 별도 오류로 구분합니다.
    private fun validateConfirmationStatus(rental: Rental) {
        if (rental.status == RentalStatus.COMPLETED || rental.status == RentalStatus.DISPUTED) {
            throw CustomException(ErrorCode.RETURN_ALREADY_CONFIRMED)
        }
        if (rental.status != RentalStatus.RETURNED) {
            throw CustomException(ErrorCode.INVALID_RETURN_CONFIRMATION_STATUS)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ReturnConfirmationService::class.java)
    }
}
