package iter.device.storage

data class ValidatedUpload(
    val objectKey: String,
    val contentType: String,
    val size: Long,
    val eTag: String,
)
