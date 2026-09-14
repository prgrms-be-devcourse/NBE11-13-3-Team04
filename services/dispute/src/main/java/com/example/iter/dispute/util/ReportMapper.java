package com.example.iter.dispute.util;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.dispute.domain.entity.Report;
import com.example.iter.dispute.dto.response.ReportDetailResponse;
import com.example.iter.dispute.dto.response.ReportSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class ReportMapper {

    // 신고와 신고자 정보를 신고 목록 응답으로 변환합니다.
    public ReportSummaryResponse toSummary(Report report, UserSummary reporter) {
        return new ReportSummaryResponse(
                report.getId(),
                reporter,
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    // 신고와 신고자 정보를 신고 상세 응답으로 변환합니다.
    public ReportDetailResponse toDetail(Report report, UserSummary reporter) {
        return new ReportDetailResponse(
                report.getId(),
                reporter,
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedAt()
        );
    }
}
