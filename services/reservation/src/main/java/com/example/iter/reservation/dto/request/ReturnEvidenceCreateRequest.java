package com.example.iter.reservation.dto.request;

import com.example.iter.common.dto.request.CapturedImageRequest;
import com.example.iter.reservation.domain.entity.ProductConditionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReturnEvidenceCreateRequest(
        @NotNull(message = "반납 시점 상품 상태는 필수입니다.")
        ProductConditionType productCondition,

        String conditionDetail,

        @Size(min = 3, max = 3, message = "반납 사진은 정면·측면·후면 각각 한 장씩 필요합니다.")
        List<@Valid CapturedImageRequest> images
) {
    @AssertTrue(message = "반납 사진은 정면·측면·후면 각각 한 장씩 필요합니다.")
    public boolean hasAllCaptureViews() {
        return CaptureImageRequestRules.hasAllViews(images);
    }

    @AssertTrue(message = "중복된 반납 사진은 제출할 수 없습니다.")
    public boolean hasNoDuplicateKeys() {
        return CaptureImageRequestRules.hasNoDuplicateKeys(images);
    }
}
