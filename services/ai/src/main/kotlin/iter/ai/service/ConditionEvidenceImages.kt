package iter.ai.service

import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.common.storage.S3StorageProperties
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import java.net.URI
import java.util.regex.Pattern

@Service
class ConditionEvidenceImages(private val s3: S3Client, private val properties: S3StorageProperties) {
    // 허용된 증빙 URL을 S3 메타데이터와 함께 고정된 AI 이미지 참조로 변환합니다.
    fun reference(imageUrl: String?, imageId: String): Map<String, Any> =
        reference(imageUrl, imageId, "USER_SELECTED")

    // 촬영 방향이 정해진 V2 요청에서는 사용자가 지정한 방향을 AI 참조에 함께 넣습니다.
    fun reference(imageUrl: String?, imageId: String, captureSlot: String): Map<String, Any> {
        val key = objectKey(imageUrl)

        // 본문을 다시 내려받지 않고 HEAD 요청으로 현재 객체의 버전과 형식만 확인합니다.
        val request = HeadObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .build()

        val metadata = s3.headObject(request)

        val contentLength: Long? = metadata.contentLength()
        val contentType: String? = metadata.contentType()
        val etag: String? = metadata.eTag()

        val invalidSize = contentLength == null || contentLength <= 0 || contentLength > MAX_IMAGE_SIZE
        val invalidContentType = contentType == null || contentType !in ALLOWED_CONTENT_TYPES
        val missingEtag = etag.isNullOrBlank()

        if (invalidSize || invalidContentType || missingEtag) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        return java.util.Map.of(
            "imageId",
            imageId,
            "objectKey",
            key,
            "captureSlot",
            captureSlot,
            "etag",
            etag,
            "contentType",
            contentType,
            "sizeBytes",
            contentLength
        )
    }

    // 서명 URL도 신뢰하지 않고 현재 버킷의 증빙 경로와 UUID 파일명만 object key로 변환합니다.
    fun objectKey(url: String?): String {
        if (url.isNullOrBlank()) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        val uri = try {
            URI.create(url)
        } catch (exception: IllegalArgumentException) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        val s3Host = "${properties.bucket}.s3.${properties.region}.amazonaws.com"
        val publicHost = publicBaseHost()
        val allowedHost = s3Host.equals(uri.host, ignoreCase = true) ||
            (publicHost != null && publicHost.equals(uri.host, ignoreCase = true))

        if (!"https".equals(uri.scheme, ignoreCase = true) || !allowedHost || uri.fragment != null) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        var key = uri.path ?: throw CustomException(ErrorCode.INVALID_IMAGE)

        while (key.startsWith("/")) {
            key = key.substring(1)
        }

        val uuidFile = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
            "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)"

        val quotedPrefix = Pattern.quote(normalizedPrefix())

        val privatePattern = quotedPrefix +
            "/private/rental-evidence/[0-9]+/[0-9]+/(receipt|return)/" +
            "(?:(front|side|rear)/)?$uuidFile"

        val legacyPattern = "$quotedPrefix/public/rental-evidence/$uuidFile"
        val listingPattern = "$quotedPrefix/public/[0-9]+/$uuidFile"

        if (
            !Pattern.matches(privatePattern, key) &&
            !Pattern.matches(legacyPattern, key) &&
            !Pattern.matches(listingPattern, key)
        ) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        return key
    }

    private fun publicBaseHost(): String? {
        val base = properties.publicBaseUrl

        if (base.isNullOrBlank()) {
            return null
        }

        return try {
            URI.create(base).host
        } catch (exception: IllegalArgumentException) {
            null
        }
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
        private const val MAX_IMAGE_SIZE = 20_000_000L
        private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
