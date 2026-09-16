package com.example.iter.reservation.service

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.delivery.api.ShippingCommandPort
import com.example.iter.device.api.EquipmentInfo
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Receipt
import com.example.iter.reservation.domain.entity.ReceiptImage
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.entity.ReturnReceipt
import com.example.iter.reservation.domain.entity.ReturnReceiptImage
import com.example.iter.reservation.domain.repository.ReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReceiptRepository
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptImageRepository
import com.example.iter.reservation.domain.repository.ReturnReceiptRepository
import com.example.iter.reservation.dto.request.ReceiptCreateRequest
import com.example.iter.reservation.dto.request.ReturnEvidenceCreateRequest
import com.example.iter.reservation.dto.request.ShippingRegisterRequest
import com.example.iter.reservation.dto.response.ReceiptCreateResponse
import com.example.iter.reservation.dto.response.ReturnEvidenceCreateResponse
import com.example.iter.reservation.dto.response.ReturnRequestResponse
import com.example.iter.reservation.dto.response.ShippingRegisterResponse
import com.example.iter.reservation.event.RentalReceivedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDateTime

// 대여 상태머신 중 "승인 이후 ~ 반납 도착"(APPROVED->SHIPPING->RENTING->RETURN_REQUESTED->RETURNED)
// 구간을 다룬다. RETURNED->COMPLETED/DISPUTED 최종 확인은 ReturnService(기존 구현)가 담당.
//
// RECEIVED/RETURNING은 ERD상 별도 상태로 정의돼 있지만, 이 서비스는 "수령확인 = 대여 시작"
// "반납증빙 제출 = 반납 도착"으로 한 요청 안에서 곧바로 다음 상태까지 묶어 처리한다 — 프론트
// UX가 그 중간 상태를 별도 액션으로 노출하지 않기 때문 (RentalStatus enum 값 자체는 그대로 유지).
//
// 이 서비스는 apps/monolith 어디서도 @MockitoBean/@Mock으로 모킹되지 않아(실제 통합 테스트로만
// 검증) Long 파라미터를 non-null로 둬도 안전하다 — R6/R7의 다른 서비스와 달리 Mockito any()/
// boxed Long 함정이 적용되지 않는다.
@Service
@Transactional
class RentalFulfillmentService(
    private val rentalRepository: RentalRepository,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val shippingCommandPort: ShippingCommandPort,
    private val receiptRepository: ReceiptRepository,
    private val receiptImageRepository: ReceiptImageRepository,
    private val returnReceiptRepository: ReturnReceiptRepository,
    private val returnReceiptImageRepository: ReturnReceiptImageRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    // 장비 등록자가 출고 배송 정보를 등록합니다. APPROVED -> SHIPPING
    // mock 배송이라 실제 택배사 연동/배송 추적이 없다 — 등록 즉시 배송완료로 기록한다.
    fun registerShipping(ownerId: Long, rentalId: Long, request: ShippingRegisterRequest): ShippingRegisterResponse {
        val rental = findRental(rentalId)
        val equipment = findEquipment(rental.equipmentId)
        validateOwner(ownerId, equipment)

        if (rental.status != RentalStatus.APPROVED) {
            throw CustomException(ErrorCode.RENTAL_NOT_SHIPPABLE)
        }

        val now = LocalDateTime.now()
        shippingCommandPort.recordOutboundDelivered(rental.id, request.carrier(), request.trackingNumber(), now)

        rental.changeStatus(RentalStatus.SHIPPING)
        log.info(
            "대여 출고 처리: rentalId={}, equipmentId={}, ownerId={}, status={}",
            rentalId, rental.equipmentId, ownerId, rental.status,
        )

        return ShippingRegisterResponse(rental.id, rental.status, request.carrier(), request.trackingNumber(), now)
    }

    // 대여자가 수령 증빙을 제출합니다. SHIPPING -> RENTING (수령확인 = 대여 시작)
    fun createReceipt(renterId: Long, rentalId: Long, request: ReceiptCreateRequest): ReceiptCreateResponse {
        val rental = findRental(rentalId)
        validateRenter(renterId, rental)

        if (rental.status != RentalStatus.SHIPPING) {
            throw CustomException(ErrorCode.RENTAL_NOT_RECEIVABLE)
        }

        val now = LocalDateTime.now()
        val receipt = receiptRepository.save(
            Receipt(rental, request.productCondition()!!, request.conditionDetail(), now),
        )
        saveReceiptImages(receipt, request.imageUrls()!!)

        rental.changeStatus(RentalStatus.RENTING)
        eventPublisher.publishEvent(RentalReceivedEvent(rental.id))
        log.info("대여 수령 처리: rentalId={}, renterId={}, status={}", rentalId, renterId, rental.status)

        return ReceiptCreateResponse(rental.id, rental.status, now)
    }

    // 대여자가 반납을 신청합니다. RENTING -> RETURN_REQUESTED
    fun requestReturn(renterId: Long, rentalId: Long): ReturnRequestResponse {
        val rental = findRental(rentalId)
        validateRenter(renterId, rental)

        if (rental.status != RentalStatus.RENTING) {
            throw CustomException(ErrorCode.RENTAL_NOT_RETURN_REQUESTABLE)
        }

        rental.changeStatus(RentalStatus.RETURN_REQUESTED)
        log.info("대여 반납 신청 처리: rentalId={}, renterId={}, status={}", rentalId, renterId, rental.status)
        return ReturnRequestResponse(rental.id, rental.status)
    }

    // 대여자가 반납 증빙을 제출합니다. RETURN_REQUESTED -> RETURNED (증빙 제출 = 반납 도착)
    // 이후 등록자의 최종 확인(ReturnService.confirmReturn)이 RETURNED -> COMPLETED/DISPUTED를 처리한다.
    fun createReturnEvidence(
        renterId: Long,
        rentalId: Long,
        request: ReturnEvidenceCreateRequest,
    ): ReturnEvidenceCreateResponse {
        val rental = findRental(rentalId)
        validateRenter(renterId, rental)

        if (rental.status != RentalStatus.RETURN_REQUESTED) {
            throw CustomException(ErrorCode.RENTAL_NOT_RETURN_EVIDENCE_SUBMITTABLE)
        }

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val returnReceipt = returnReceiptRepository.save(
            ReturnReceipt(rental, request.productCondition()!!, request.conditionDetail(), today),
        )
        saveReturnReceiptImages(returnReceipt, request.imageUrls()!!)

        shippingCommandPort.recordReturnDelivered(rental.id, now)

        rental.changeStatus(RentalStatus.RETURNED)
        log.info("대여 반납 증빙 등록 처리: rentalId={}, renterId={}, status={}", rentalId, renterId, rental.status)

        return ReturnEvidenceCreateResponse(rental.id, rental.status, today)
    }

    private fun saveReceiptImages(receipt: Receipt, imageUrls: List<String>) {
        val images = imageUrls.mapIndexed { index, imageUrl -> ReceiptImage(receipt, imageUrl, index) }
        receiptImageRepository.saveAll(images)
    }

    private fun saveReturnReceiptImages(returnReceipt: ReturnReceipt, imageUrls: List<String>) {
        val images = imageUrls.mapIndexed { index, imageUrl -> ReturnReceiptImage(returnReceipt, imageUrl, index) }
        returnReceiptImageRepository.saveAll(images)
    }

    private fun findRental(rentalId: Long): Rental =
        rentalRepository.findById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    private fun findEquipment(equipmentId: Long): EquipmentInfo =
        equipmentQueryPort.find(equipmentId).orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

    private fun validateOwner(ownerId: Long, equipment: EquipmentInfo) {
        if (!equipment.isOwnedBy(ownerId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    private fun validateRenter(renterId: Long, rental: Rental) {
        if (!rental.isRenter(renterId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RentalFulfillmentService::class.java)
    }
}
