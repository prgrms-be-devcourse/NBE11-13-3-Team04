package iter.auth.service

import iter.auth.domain.entity.User
import iter.auth.domain.repository.UserRepository
import iter.auth.dto.request.PasswordChangeRequest
import iter.auth.dto.request.UserDeleteRequest
import iter.auth.dto.request.UserUpdateRequest
import iter.auth.dto.response.UserResponse
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.device.api.EquipmentCommandPort
import iter.reservation.api.RentalQueryPort
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger(UserAccountService::class.java)

@Service
class UserAccountService(
    private val userRepository: UserRepository,
    private val rentalQueryPort: RentalQueryPort,
    private val equipmentCommandPort: EquipmentCommandPort,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenService: RefreshTokenService,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): UserResponse = UserResponse.from(findUser(userId))

    @Transactional
    fun updateMyProfile(userId: Long, request: UserUpdateRequest): UserResponse {
        val user = findUserWithLock(userId)
        user.updateProfile(
            request.name() ?: user.name,
            request.nickname() ?: user.nickname,
            request.phone() ?: user.phone,
            request.preferredLanguage() ?: user.preferredLanguage,
        )
        log.info("회원 프로필 변경 처리: userId={}", userId)
        return UserResponse.from(user)
    }

    @Transactional
    fun changePassword(userId: Long, request: PasswordChangeRequest) {
        val user = findUserWithLock(userId)
        val encodedCurrentPassword = user.password
            ?: throw CustomException(ErrorCode.PASSWORD_NOT_SET)

        if (!passwordEncoder.matches(request.currentPassword, encodedCurrentPassword)) {
            throw CustomException(ErrorCode.INVALID_PASSWORD)
        }
        if (passwordEncoder.matches(request.newPassword, encodedCurrentPassword)) {
            throw CustomException(ErrorCode.SAME_PASSWORD_NOT_ALLOWED)
        }

        user.changePassword(passwordEncoder.encode(request.newPassword))
        refreshTokenService.revokeAllByUserId(userId)
        log.info("비밀번호 변경 및 기존 세션 만료 처리: userId={}", userId)
    }

    @Transactional
    fun withdraw(userId: Long, request: UserDeleteRequest?) {
        val user = findUserWithLock(userId)
        validateWithdrawalPassword(user, request?.password)

        if (hasWithdrawalBlockingRental(userId)) {
            throw CustomException(ErrorCode.ACTIVE_RENTAL_EXISTS)
        }

        val withdrawnAt = LocalDateTime.now()
        // user.withdraw() 보다 먼저 호출한다 — 순서를 바꾸면 벌크 UPDATE 의 flush 시점이 달라진다.
        equipmentCommandPort.deactivateAllOwnedBy(userId, withdrawnAt)
        user.withdraw(withdrawnAt)
        refreshTokenService.revokeAllByUserId(userId)
        log.info("회원 탈퇴 처리: userId={}", userId)
    }

    private fun validateWithdrawalPassword(user: User, rawPassword: String?) {
        val encodedPassword = user.password ?: return
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw CustomException(ErrorCode.INVALID_PASSWORD)
        }
    }

    private fun hasWithdrawalBlockingRental(userId: Long): Boolean =
        // 어떤 상태가 탈퇴를 막는지는 reservation 이 판단한다.
        rentalQueryPort.hasWithdrawalBlockingRental(userId)

    private fun findUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

    private fun findUserWithLock(userId: Long): User =
        userRepository.findWithLockById(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
}
