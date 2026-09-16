package com.example.iter.dispute.support;

import com.example.iter.dispute.api.DisputeCommandPort;
import com.example.iter.dispute.api.ReturnDisputeCommand;
import com.example.iter.dispute.api.ReturnDisputeResult;
import com.example.iter.dispute.domain.entity.Dispute;
import com.example.iter.dispute.domain.entity.Report;
import com.example.iter.dispute.domain.entity.ReportTargetType;
import com.example.iter.dispute.domain.repository.DisputeRepository;
import com.example.iter.dispute.domain.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// dispute/api/DisputeCommandPort 의 모놀리스 구현.
// 엔티티 조립이 여기로 들어왔다 — 초기 상태(REPORTED)는 Dispute 의 @Builder.Default 가 정한다.
//
// !! propagation = MANDATORY 다 !!
// 분쟁 생성과 대여 상태 변경(DISPUTED)이 함께 커밋되거나 함께 롤백돼야 한다.
@Component
@RequiredArgsConstructor
public class JpaDisputeCommandAdapter implements DisputeCommandPort {

    private final DisputeRepository disputeRepository;
    private final ReportRepository reportRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReturnDisputeResult openReturnDispute(ReturnDisputeCommand command) {
        Dispute dispute = disputeRepository.save(Dispute.builder()
                .rentalId(command.rentalId())
                .reporterId(command.reporterId())
                .respondentId(command.respondentId())
                .reason(command.reason())
                .description(command.description())
                .build());
        Report report = reportRepository.save(Report.builder()
                .reporterId(command.reporterId())
                .targetType(ReportTargetType.RENTAL)
                .targetId(command.rentalId())
                .reason(command.reason())
                .description(command.description())
                .build());
        return new ReturnDisputeResult(dispute.getId(), report.getId());
    }
}
