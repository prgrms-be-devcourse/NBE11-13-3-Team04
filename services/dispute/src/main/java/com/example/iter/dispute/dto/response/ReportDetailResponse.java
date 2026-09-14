package com.example.iter.dispute.dto.response;

import com.example.iter.auth.api.UserSummary;
import com.example.iter.dispute.domain.entity.ReportStatus;
import com.example.iter.dispute.domain.entity.ReportTargetType;

import java.time.LocalDateTime;

public record ReportDetailResponse(
        Long reportId,
        UserSummary reporter,
        ReportTargetType targetType,
        Long targetId,
        String reason,
        String description,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
