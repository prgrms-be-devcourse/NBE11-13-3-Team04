package com.example.iter.composition.ai

import com.example.iter.ai.port.ConditionAnalysisContextPort
import com.example.iter.ai.port.ConditionComparisonContext
import com.example.iter.ai.port.ConditionImage
import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.UserStatus
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.Rental
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.service.ReturnService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

// 반납 비교 AI에 필요한 예약·장비·회원 정보를 애플리케이션 조합 계층에서 제공합니다.
@Component
class ConditionAnalysisContextAdapter(
    private val users: UserRepository,
    private val rentals: RentalRepository,
    private val equipment: EquipmentQueryPort,
    private val returnService: ReturnService
) : ConditionAnalysisContextPort {
    // AI 결과 조회에는 소유권만 필요하므로 쓰기 락 없이 현재 상태를 확인합니다.
    override fun requireOwner(ownerId: Long, rentalId: Long) {
        val owner = findActiveUser(ownerId)
        val rental = findRental(rentalId)
        validateOwner(owner.id!!, rental)
    }

    // S3 자료를 불러오기 전에 권한과 상태를 선검증해 불필요한 외부 호출을 줄입니다.
    override fun requireReady(ownerId: Long, rentalId: Long) {
        val owner = findActiveUser(ownerId)
        val rental = findRental(rentalId)
        validateOwner(owner.id!!, rental)
        validateReady(rental)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockAndRequireReady(ownerId: Long, rentalId: Long) {
        // 회원을 먼저, 대여를 나중에 잠그는 순서를 모든 요청에서 동일하게 유지합니다.
        val owner = users.findWithLockById(ownerId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        validateActive(owner)

        val rental = rentals.findWithLockById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

        validateOwner(owner.id!!, rental)
        validateReady(rental)
    }

    // ReturnService의 응답 계약을 AI가 의존하는 최소 이미지 모델로 변환합니다.
    override fun loadComparison(ownerId: Long, rentalId: Long): ConditionComparisonContext {
        val comparison = returnService.getReturnComparison(ownerId, rentalId)

        return ConditionComparisonContext(
            listingImages = comparison.listingImages.map { ConditionImage(it.captureView?.name, it.imageUrl) },
            beforeImages = comparison.receipt?.images.orEmpty().map { ConditionImage(it.captureView?.name, it.imageUrl) },
            afterImages = comparison.returnReceipt?.images.orEmpty().map { ConditionImage(it.captureView?.name, it.imageUrl) }
        )
    }

    private fun findActiveUser(userId: Long): User {
        val user = users.findById(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        validateActive(user)

        return user
    }

    private fun findRental(rentalId: Long): Rental = rentals.findById(rentalId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

    private fun validateOwner(ownerId: Long, rental: Rental) {
        val item = equipment.find(rental.equipmentId).orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

        if (!item.isOwnedBy(ownerId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }

    private fun validateReady(rental: Rental) {
        if (rental.status != RentalStatus.RETURNED) {
            throw CustomException(ErrorCode.INVALID_RETURN_CONFIRMATION_STATUS)
        }
    }

    private fun validateActive(user: User) {
        when (user.status) {
            UserStatus.SUSPENDED -> throw CustomException(ErrorCode.USER_SUSPENDED)
            UserStatus.DELETED -> throw CustomException(ErrorCode.USER_DELETED)
            UserStatus.ACTIVE -> Unit
        }
    }
}
