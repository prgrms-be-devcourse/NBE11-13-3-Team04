package com.example.iter.dispute.service

import com.example.iter.auth.api.UserQueryPort
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.device.api.EquipmentQueryPort
import com.example.iter.dispute.domain.entity.ReportTargetType
import com.example.iter.reservation.api.RentalQueryPort
import org.springframework.stereotype.Component

@Component
class ReportTargetValidator(
    private val userQueryPort: UserQueryPort,
    private val equipmentQueryPort: EquipmentQueryPort,
    private val rentalQueryPort: RentalQueryPort
) {
    // 대상 종류별 존재 여부·자기 신고·거래 당사자 정책을 한 곳에서 적용합니다.
    fun validate(targetType: ReportTargetType, targetId: Long, reporterId: Long) {
        when (targetType) {
            ReportTargetType.USER -> validateUserTarget(targetId, reporterId)
            ReportTargetType.EQUIPMENT -> validateEquipmentTarget(targetId, reporterId)
            ReportTargetType.RENTAL -> validateRentalTarget(targetId, reporterId)
        }
    }

    // 자기 자신은 신고할 수 없고 현재 신고 가능한 회원만 대상으로 허용합니다.
    private fun validateUserTarget(targetId: Long, reporterId: Long) {
        if (targetId == reporterId) {
            throw CustomException(ErrorCode.REPORT_SELF_TARGET_NOT_ALLOWED)
        }
        if (!userQueryPort.isReportable(targetId)) {
            throw CustomException(ErrorCode.USER_NOT_FOUND)
        }
    }

    // 삭제된 장비와 본인 소유 장비는 신고 대상으로 허용하지 않습니다.
    private fun validateEquipmentTarget(targetId: Long, reporterId: Long) {
        val equipment = equipmentQueryPort.find(targetId).orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }

        if (equipment.deleted) {
            throw CustomException(ErrorCode.EQUIPMENT_NOT_FOUND)
        }
        if (equipment.isOwnedBy(reporterId)) {
            throw CustomException(ErrorCode.REPORT_SELF_TARGET_NOT_ALLOWED)
        }
    }

    // 거래 신고는 생성 당시 대여자 또는 등록자 스냅샷에 포함된 회원만 허용합니다.
    private fun validateRentalTarget(targetId: Long, reporterId: Long) {
        val rental = rentalQueryPort.find(targetId).orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

        if (!rental.isRenter(reporterId) && !rental.isOwner(reporterId)) {
            throw CustomException(ErrorCode.RENTAL_NOT_PARTY)
        }
    }
}
