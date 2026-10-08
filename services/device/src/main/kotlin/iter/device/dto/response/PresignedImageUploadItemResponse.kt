package iter.device.dto.response

import iter.common.image.CaptureView
import java.time.LocalDateTime

data class PresignedImageUploadItemResponse(
    val captureView: CaptureView?,
    val objectKey: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
    val expiresAt: LocalDateTime
)
