package com.example.iter.auth.service

import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.auth.dto.request.AdminUserSearchRequest
import com.example.iter.auth.dto.request.AdminUserStatusRequest
import com.example.iter.auth.dto.response.AdminUserDetailResponse
import com.example.iter.auth.dto.response.AdminUserStatusResponse
import com.example.iter.auth.dto.response.AdminUserSummaryResponse
import com.example.iter.auth.util.AdminUserMapper
import com.example.iter.common.audit.domain.entity.AdminActionTargetType
import com.example.iter.common.audit.domain.entity.AdminActionType
import com.example.iter.common.audit.service.AdminActionService
import com.example.iter.common.dto.response.CursorPageResponse
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.pagination.CursorCodec
import com.example.iter.common.pagination.CursorKey
import com.example.iter.common.security.Role
import com.example.iter.common.security.UserStatus
import com.example.iter.dispute.api.ReportQueryPort
import com.example.iter.reservation.api.RentalQueryPort
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.time.Clock
import java.time.LocalDate

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val rentalQueryPort: RentalQueryPort,
    private val reportQueryPort: ReportQueryPort,
    private val adminActionService: AdminActionService,
    private val adminUserMapper: AdminUserMapper,
    private val clock: Clock
) {
    // 관리자 회원 목록을 검색 조건과 커서 정보로 조회합니다.
    @Transactional(readOnly = true)
    fun getUsers(request: AdminUserSearchRequest): CursorPageResponse<AdminUserSummaryResponse> {
        val keyword = request.keyword?.takeIf(StringUtils::hasText)?.trim()

        val cursorKey = CursorCodec.decode(request.cursor)

        val users = userRepository.searchForAdminByCursor(
            keyword,
            request.status,
            cursorKey?.createdAt,
            cursorKey?.id,
            PageRequest.of(0, request.size + 1)
        )

        return CursorPageResponse.from(
            users,
            request.size,
            AdminUserSummaryResponse::from
        ) { user -> CursorKey(user.createdAt, user.id) }
    }

    // 거래 집계 정의는 reservation, 신고 집계 정의는 dispute 포트에 위임합니다.
    @Transactional(readOnly = true)
    fun getUser(userId: Long): AdminUserDetailResponse {
        val user = userRepository.findById(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val rentalStats = rentalQueryPort.countUserRentalStats(userId, LocalDate.now(clock))

        return adminUserMapper.toDetail(
            user,
            rentalStats.rentedCount,
            rentalStats.lentCount,
            rentalStats.overdueCount,
            reportQueryPort.countAgainstUser(userId)
        )
    }

    // 회원 상태를 변경하고 같은 트랜잭션에 관리자 처리 이력을 저장합니다.
    @Transactional
    fun updateStatus(adminId: Long, userId: Long, request: AdminUserStatusRequest): AdminUserStatusResponse {
        val user = userRepository.findWithLockById(userId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val requestedStatus = requireNotNull(request.status)

        validateStatusChange(user, requestedStatus)

        val action = applyStatus(user, requestedStatus)

        adminActionService.record(
            adminId,
            AdminActionTargetType.USER,
            user.id,
            action,
            requireNotNull(request.reason).trim()
        )
        userRepository.flush()

        log.info(
            "관리자 회원 상태 변경 처리: adminId={}, userId={}, action={}, status={}",
            adminId,
            userId,
            action,
            user.status
        )

        return AdminUserStatusResponse.from(user)
    }

    // 관리자 정지와 탈퇴 회원 복구, 동일 상태 재요청처럼 허용하지 않은 상태 전이를 차단합니다.
    private fun validateStatusChange(user: User, requestedStatus: UserStatus) {
        if (user.role == Role.ADMIN && requestedStatus == UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.ADMIN_SUSPENSION_NOT_ALLOWED)
        }

        if (user.status == UserStatus.DELETED || user.status == requestedStatus) {
            throw CustomException(ErrorCode.INVALID_USER_STATUS_TRANSITION)
        }
    }

    // 도메인 상태 변경과 감사 이력에 기록할 조치 유형을 함께 결정합니다.
    private fun applyStatus(user: User, requestedStatus: UserStatus): AdminActionType = when (requestedStatus) {
        UserStatus.SUSPENDED -> {
            user.suspend()
            AdminActionType.SUSPEND_USER
        }

        UserStatus.ACTIVE -> {
            user.restore()
            AdminActionType.RESTORE_USER
        }

        UserStatus.DELETED -> throw CustomException(ErrorCode.INVALID_USER_STATUS_TRANSITION)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AdminUserService::class.java)
    }
}
