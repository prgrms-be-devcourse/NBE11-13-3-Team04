package com.example.iter.reservation.service

import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import com.example.iter.device.api.EquipmentInfo
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.entity.RentalReview
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.domain.repository.RentalReviewRepository
import com.example.iter.reservation.dto.request.RentalReviewCreateRequest
import com.example.iter.reservation.dto.response.RentalReviewResponse
import com.example.iter.reservation.dto.response.UserReviewStatsResponse
import com.example.iter.reservation.event.RentalReviewCreatedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RentalReviewService(
    private val rentalReviewRepository: RentalReviewRepository,
    private val rentalRepository: RentalRepository,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val eventPublisher: ApplicationEventPublisher,
) {

    // 거래 당사자(대여자 또는 장비 등록자)가 상대방에게 리뷰를 남깁니다.
    @Transactional
    fun createReview(reviewerId: Long, rentalId: Long, request: RentalReviewCreateRequest): RentalReviewResponse {
        val rental = findRental(rentalId)
        val equipment = findEquipment(rental.equipmentId)

        val revieweeId = resolveRevieweeId(reviewerId, rental, equipment)

        val saved = rentalReviewRepository.save(
            RentalReview(
                rental.id!!,
                reviewerId,
                revieweeId,
                request.rating()!!,
                request.content()!!.trim(),
            )
        )

        eventPublisher.publishEvent(RentalReviewCreatedEvent(saved.id!!))

        return RentalReviewResponse.from(saved)
    }

    // 거래 당사자가 해당 거래에 달린 리뷰(최대 2건, 대여자/등록자 각 1건)를 조회합니다.
    @Transactional(readOnly = true)
    fun getReviewsForRental(userId: Long, rentalId: Long): List<RentalReviewResponse> {
        val rental = findRental(rentalId)
        val equipment = findEquipment(rental.equipmentId)
        validateParty(userId, rental, equipment)

        return rentalReviewRepository.findAllByRentalId(rentalId).map { RentalReviewResponse.from(it) }
    }

    // 특정 사용자가 받은 리뷰 목록을 커서(keyset) 방식으로 조회합니다.
    @Transactional(readOnly = true)
    fun getReviewsForUser(userId: Long, cursor: String?, size: Int): CursorPageResponse<RentalReviewResponse> {
        val cursorKey = CursorCodec.decode(cursor)
        val cursorCreatedAt = cursorKey?.createdAt()
        val cursorId = cursorKey?.id()

        val limit = PageRequest.of(0, size + 1)
        val reviews = rentalReviewRepository.findNextByRevieweeId(userId, cursorCreatedAt, cursorId, limit)

        return CursorPageResponse.from(
            reviews,
            size,
            { RentalReviewResponse.from(it) },
            { review -> CursorKey(review.createdAt, review.id) },
        )
    }

    // 특정 사용자가 받은 리뷰의 평균 평점과 개수를 조회합니다.
    @Transactional(readOnly = true)
    fun getReviewStats(userId: Long): UserReviewStatsResponse =
        UserReviewStatsResponse.from(rentalReviewRepository.findRatingStatsByRevieweeId(userId))

    // 특정 사용자가 작성한 리뷰 목록을 커서(keyset) 방식으로 조회합니다.
    @Transactional(readOnly = true)
    fun getReviewsWrittenByUser(userId: Long, cursor: String?, size: Int): CursorPageResponse<RentalReviewResponse> {
        val cursorKey = CursorCodec.decode(cursor)
        val cursorCreatedAt = cursorKey?.createdAt()
        val cursorId = cursorKey?.id()

        val limit = PageRequest.of(0, size + 1)
        val reviews = rentalReviewRepository.findNextByReviewerId(userId, cursorCreatedAt, cursorId, limit)

        return CursorPageResponse.from(
            reviews,
            size,
            { RentalReviewResponse.from(it) },
            { review -> CursorKey(review.createdAt, review.id) },
        )
    }

    // ===================================================

    private fun findRental(rentalId: Long): Rental =
        rentalRepository.findById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    private fun findEquipment(equipmentId: Long): EquipmentInfo =
        equipmentQueryPort.find(equipmentId).orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

    private fun validateParty(userId: Long, rental: Rental, equipment: EquipmentInfo) {
        val renter = rental.isRenter(userId)
        val owner = equipment.isOwnedBy(userId)

        if (!renter && !owner) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    // 로그인 사용자가 이 거래의 대여자인지 등록자인지 판별해 리뷰 대상(revieweeId)을 정하고,
    // 리뷰 작성이 가능한 상태인지(반납 완료 여부, 중복 작성 여부) 검증합니다.
    private fun resolveRevieweeId(reviewerId: Long, rental: Rental, equipment: EquipmentInfo): Long {
        val revieweeId = if (rental.isRenter(reviewerId)) {
            equipment.ownerId
        } else if (equipment.isOwnedBy(reviewerId)) {
            rental.renterId
        } else {
            throw CustomException(ErrorCode.FORBIDDEN)
        }

        if (rental.status != RentalStatus.COMPLETED) {
            throw CustomException(ErrorCode.REVIEW_NOT_ALLOWED_STATUS)
        }

        if (rentalReviewRepository.existsByRentalIdAndReviewerId(rental.id!!, reviewerId)) {
            throw CustomException(ErrorCode.REVIEW_ALREADY_EXISTS)
        }

        return revieweeId
    }
}
