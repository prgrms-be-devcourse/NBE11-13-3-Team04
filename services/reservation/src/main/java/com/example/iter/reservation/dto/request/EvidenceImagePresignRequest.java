package com.example.iter.reservation.dto.request;

import com.example.iter.common.image.CaptureView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

// 수령/반납 증빙 사진 업로드용 presigned URL 요청 — 장비 이미지(device 도메인)와 달리
// temp->public 승격 단계가 없어 훨씬 단순하다: 발급받은 objectKey가 곧 최종 위치다.
public record EvidenceImagePresignRequest(
        @NotEmpty(message = "이미지는 최소 1장 필요합니다.")
        @Size(max = 4, message = "이미지는 최대 4장까지 업로드할 수 있습니다.")
        List<@Valid Item> files
) {
    public record Item(
            @NotNull(message = "사진 촬영 방향은 필수입니다.")
            CaptureView captureView,

            @NotBlank(message = "Content-Type은 필수입니다.")
            @Pattern(
                    regexp = "image/jpeg|image/png|image/webp",
                    message = "JPEG, PNG, WebP 형식만 지원합니다."
            )
            String contentType,

            @Positive(message = "이미지 크기는 0보다 커야 합니다.")
            @Max(value = 10_485_760, message = "이미지는 한 장당 10MB 이하여야 합니다.")
            long size
    ) {
    }
}
