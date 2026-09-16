package com.example.iter.device.dto.response;

import com.example.iter.common.image.CaptureView;

public record ImageResponse(
        Long imageId,
        String imageUrl,
        CaptureView captureView,
        int sortOrder,
        boolean thumbnail
) {
}
