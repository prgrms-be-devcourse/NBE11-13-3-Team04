package com.example.iter.reservation.dto.request

import com.example.iter.common.dto.request.CapturedImageRequest
import com.example.iter.common.image.CaptureView
import java.util.EnumSet

// 수령과 반납 요청이 같은 세 촬영 방향 검증을 공유하도록 모아둔 내부 규칙입니다.
object CaptureImageRequestRules {
    @JvmStatic
    fun hasAllViews(images: List<CapturedImageRequest>?): Boolean {
        if (images == null || images.size != CaptureView.entries.size) {
            return false
        }

        val views = EnumSet.noneOf(CaptureView::class.java)

        images.mapNotNull(CapturedImageRequest::captureView).forEach(views::add)

        return views == EnumSet.allOf(CaptureView::class.java)
    }

    @JvmStatic
    fun hasNoDuplicateKeys(images: List<CapturedImageRequest>?): Boolean =
        images == null || images.map(CapturedImageRequest::objectKey).toSet().size == images.size
}
