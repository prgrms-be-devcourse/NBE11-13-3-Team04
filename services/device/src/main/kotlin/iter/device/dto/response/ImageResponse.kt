package iter.device.dto.response

import iter.common.image.CaptureView

data class ImageResponse(
    val imageId: Long,
    val imageUrl: String,
    val captureView: CaptureView?,
    val sortOrder: Int,
    val thumbnail: Boolean
)
