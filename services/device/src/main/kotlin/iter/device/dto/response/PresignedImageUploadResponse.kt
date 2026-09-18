package iter.device.dto.response

data class PresignedImageUploadResponse(
    val uploads: List<PresignedImageUploadItemResponse>,
)
