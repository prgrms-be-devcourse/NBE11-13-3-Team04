package com.example.iter.dispute.api;

import java.util.Objects;

// 반납 분쟁 생성에 필요한 값. 원시값만 담고 엔티티는 담지 않는다.
public record ReturnDisputeCommand(
        Long rentalId,
        Long reporterId,
        Long respondentId,
        String reason,
        String description
) {
    public ReturnDisputeCommand {
        Objects.requireNonNull(rentalId, "rentalId");
        Objects.requireNonNull(reporterId, "reporterId");
        Objects.requireNonNull(respondentId, "respondentId");
    }
}
