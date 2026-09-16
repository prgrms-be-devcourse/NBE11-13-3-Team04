package com.example.iter.device.dto.response;

import com.example.iter.common.image.CaptureView;

import java.time.LocalDateTime;
import java.util.Map;

public record PresignedImageUploadItemResponse(
        CaptureView captureView,
        String objectKey,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        LocalDateTime expiresAt
) {
}
