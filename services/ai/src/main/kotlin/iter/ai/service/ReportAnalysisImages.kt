package iter.ai.service

import iter.common.storage.S3StorageProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import java.net.URI
import java.util.LinkedHashMap
import java.util.Locale
import java.util.Optional

@Service
class ReportAnalysisImages(private val s3: S3Client, private val properties: S3StorageProperties) {
    // DB의 이미지 경로를 검사하고 OpenAI가 읽을 수 있는 고정 버전 참조로 변환합니다.
    fun reference(storedPath: String?, imageId: String?, captureSlot: String?): Optional<Map<String, Any?>> {
        return try {
            val objectKey = objectKey(storedPath)

            val metadata = s3.headObject(
                HeadObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .build()
            )

            val size: Long? = metadata.contentLength()
            val contentType: String? = metadata.contentType()
            val etag: String? = metadata.eTag()

            val validSize = size != null && size > 0 && size <= MAX_IMAGE_SIZE
            val validType = contentType != null && contentType in ALLOWED_CONTENT_TYPES
            val hasVersion = !etag.isNullOrBlank()

            if (!validSize || !validType || !hasVersion) {
                Optional.empty()
            } else {
                val reference = LinkedHashMap<String, Any?>()

                reference["imageId"] = imageId
                reference["objectKey"] = objectKey
                reference["captureSlot"] = captureSlot
                reference["etag"] = etag
                reference["contentType"] = contentType
                reference["sizeBytes"] = size
                Optional.of(reference)
            }
        } catch (exception: RuntimeException) {
            // 오래된 URL이나 삭제된 객체 하나 때문에 신고 분석 전체가 실패하지 않게 해당 사진만 제외합니다.
            log.warn(
                "신고 AI 참고 사진을 사용할 수 없습니다. captureSlot={}, cause={}",
                captureSlot,
                exception.javaClass.simpleName
            )

            Optional.empty()
        }
    }

    // 현재 버킷의 장비 공개 경로와 대여 증빙 비공개 경로만 허용합니다.
    fun objectKey(storedPath: String?): String {
        if (storedPath.isNullOrBlank()) {
            throw IllegalArgumentException("Image path is empty")
        }

        var key = storedPath.trim()

        if (key.startsWith("https://")) {
            val uri = URI.create(key)

            if (!isAllowedHost(uri.host) || uri.fragment != null) {
                throw IllegalArgumentException("Image URL is not allowed")
            }

            key = uri.path
        } else if (key.contains("://")) {
            throw IllegalArgumentException("Image URL scheme is not allowed")
        }

        while (key.startsWith("/")) {
            key = key.substring(1)
        }

        val prefix = normalizedPrefix()
        val allowedPath = key.startsWith("$prefix/public/") || key.startsWith("$prefix/private/rental-evidence/")
        val lowerKey = key.lowercase(Locale.getDefault())

        val imageExtension = lowerKey.endsWith(".jpg") ||
            lowerKey.endsWith(".jpeg") ||
            lowerKey.endsWith(".png") ||
            lowerKey.endsWith(".webp")

        val unsafeCharacters = key.contains("..") || key.contains("\\") || key.contains("%")

        if (!allowedPath || !imageExtension || unsafeCharacters) {
            throw IllegalArgumentException("Image object key is not allowed")
        }

        return key
    }

    private fun isAllowedHost(host: String?): Boolean {
        if (host == null) {
            return false
        }

        val s3Host = "${properties.bucket}.s3.${properties.region}.amazonaws.com"

        if (s3Host.equals(host, ignoreCase = true)) {
            return true
        }

        val publicBaseUrl = properties.publicBaseUrl

        if (publicBaseUrl.isNullOrBlank()) {
            return false
        }

        return host.equals(URI.create(publicBaseUrl).host, ignoreCase = true)
    }

    private fun normalizedPrefix(): String {
        var prefix = properties.keyPrefix.trim()

        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1)
        }

        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length - 1)
        }

        return prefix
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ReportAnalysisImages::class.java)
        private const val MAX_IMAGE_SIZE = 20_000_000L
        private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
