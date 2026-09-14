package com.example.iter.admin.stats.service;

import com.example.iter.admin.stats.dto.response.AdminStatsResponse;
import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.dispute.api.ReportQueryPort;
import com.example.iter.payment.api.PaymentQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserQueryPort userQueryPort;
    private final EquipmentQueryPort equipmentQueryPort;
    private final ReportQueryPort reportQueryPort;
    private final PaymentQueryPort paymentQueryPort;

    // 관리자 대시보드 상단 카드에 필요한 회원·장비·신고·결제 건수를 각 도메인의 포트로 조회합니다.
    // 어떤 신고가 "미처리"인지는 dispute 가 판단합니다 — 여기서 ReportStatus 를 알지 않습니다.
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return AdminStatsResponse.of(
                userQueryPort.count(),
                equipmentQueryPort.count(),
                reportQueryPort.countReceived(),
                paymentQueryPort.count()
        );
    }
}
