package com.example.iter.dispute.service;

import com.example.iter.common.security.UserStatus;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.device.api.EquipmentInfo;
import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.dispute.domain.entity.ReportTargetType;
import com.example.iter.reservation.api.RentalInfo;
import com.example.iter.reservation.api.RentalQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportTargetValidator {

    private final UserQueryPort userQueryPort;
    private final EquipmentQueryPort equipmentQueryPort;
    private final RentalQueryPort rentalQueryPort;

    // 신고 대상 유형에 맞게 대상 존재 여부와 신고 권한을 검증합니다.
    public void validate(ReportTargetType targetType, Long targetId, Long reporterId) {
        switch (targetType) {
            case USER -> validateUserTarget(targetId, reporterId);
            case EQUIPMENT -> validateEquipmentTarget(targetId, reporterId);
            case RENTAL -> validateRentalTarget(targetId, reporterId);
        }
    }

    // 자기 자신과 탈퇴한 회원을 신고하지 못하도록 검증합니다.
    private void validateUserTarget(Long targetId, Long reporterId) {
        if (targetId.equals(reporterId)) {
            throw new CustomException(ErrorCode.REPORT_SELF_TARGET_NOT_ALLOWED);
        }

        // 탈퇴 회원은 "없는 회원"과 같게 취급한다 — 그 판단은 auth 가 한다.
        if (!userQueryPort.isReportable(targetId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    // 본인 소유 장비와 삭제된 장비를 신고하지 못하도록 검증합니다.
    private void validateEquipmentTarget(Long targetId, Long reporterId) {
        EquipmentInfo equipment = equipmentQueryPort.find(targetId).orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        if (equipment.deleted()) {
            throw new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND);
        }

        if (equipment.isOwnedBy(reporterId)) {
            throw new CustomException(ErrorCode.REPORT_SELF_TARGET_NOT_ALLOWED);
        }
    }

    // 거래의 대여자 또는 장비 등록자만 해당 거래를 신고할 수 있도록 검증합니다.
    private void validateRentalTarget(Long targetId, Long reporterId) {
        RentalInfo rental = rentalQueryPort.find(targetId).orElseThrow(() -> new CustomException(ErrorCode.RENTAL_NOT_FOUND));

        EquipmentInfo equipment = equipmentQueryPort.find(rental.equipmentId()).orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        if (!rental.isRenter(reporterId) && !equipment.isOwnedBy(reporterId)) {
            throw new CustomException(ErrorCode.RENTAL_NOT_PARTY);
        }
    }
}
