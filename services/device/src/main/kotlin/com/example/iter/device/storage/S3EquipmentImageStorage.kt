package com.example.iter.device.storage

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.storage.S3StorageProperties
import com.example.iter.device.service.EquipmentImagePolicy
import com.example.iter.device.support.EquipmentImageUrlResolver
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.MetadataDirective
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

import java.time.LocalDateTime
import java.util.UUID

@Component
class S3EquipmentImageStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3StorageProperties,
    private val imagePolicy: EquipmentImagePolicy,
    private val imageUrlResolver: EquipmentImageUrlResolver,
) : EquipmentImageStorage {

    override fun createPresignedUpload(
        userId: Long,
        contentType: String,
        expectedSize: Long,
    ): PresignedUpload {
        imagePolicy.validateMetadata(contentType, expectedSize)
        val objectKey = "%s/temp/%d/%s.%s".format(
            normalizedPrefix(),
            userId,
            UUID.randomUUID(),
            imagePolicy.extensionOf(contentType),
        )

        try {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(expectedSize)
                .build()
            val presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                    .signatureDuration(properties.presignedUrlValidity)
                    .putObjectRequest(putObjectRequest)
                    .build()
            )
            val expiresAt = LocalDateTime.now().plus(properties.presignedUrlValidity)
            return PresignedUpload(
                objectKey,
                presignedRequest.url(),
                clientRequiredHeaders(presignedRequest),
                expiresAt,
            )
        } catch (exception: RuntimeException) {
            throw CustomException(ErrorCode.IMAGE_UPLOAD_FAILED)
        }
    }

    override fun validateTemporaryUpload(
        objectKey: String,
        expectedContentType: String,
        expectedSize: Long,
    ): ValidatedUpload {
        try {
            val head = s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .build()
            )
            if (head.contentLength() != expectedSize || expectedContentType != head.contentType()) {
                throw CustomException(ErrorCode.INVALID_IMAGE)
            }
            imagePolicy.validateMetadata(head.contentType(), head.contentLength())

            val header = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .range("bytes=0-11")
                    .build()
            )
            if (head.eTag() != header.response().eTag()) {
                throw CustomException(ErrorCode.INVALID_IMAGE)
            }
            imagePolicy.validateSignature(head.contentType(), header.asByteArray())
            return ValidatedUpload(
                objectKey,
                head.contentType(),
                head.contentLength(),
                head.eTag(),
            )
        } catch (exception: CustomException) {
            throw exception
        } catch (exception: S3Exception) {
            if (exception.statusCode() == 404) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND)
            }
            throw CustomException(ErrorCode.IMAGE_UPLOAD_FAILED)
        } catch (exception: RuntimeException) {
            throw CustomException(ErrorCode.IMAGE_UPLOAD_FAILED)
        }
    }

    override fun promote(equipmentId: Long, upload: ValidatedUpload): StoredImage {
        val finalObjectKey = "%s/public/%d/%s.%s".format(
            normalizedPrefix(),
            equipmentId,
            UUID.randomUUID(),
            imagePolicy.extensionOf(upload.contentType),
        )
        try {
            s3Client.copyObject(
                CopyObjectRequest.builder()
                    .sourceBucket(properties.bucket)
                    .sourceKey(upload.objectKey)
                    .destinationBucket(properties.bucket)
                    .destinationKey(finalObjectKey)
                    // copySourceIfMatch + REPLACE 는 S3 복사의 낙관적 동시성 검사다. 단순화하지 말 것.
                    .copySourceIfMatch(upload.eTag)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(upload.contentType)
                    .cacheControl(CACHE_CONTROL)
                    .build()
            )
            return StoredImage(
                finalObjectKey,
                imageUrlResolver.resolve(finalObjectKey),
            )
        } catch (exception: RuntimeException) {
            throw CustomException(ErrorCode.IMAGE_UPLOAD_FAILED)
        }
    }

    override fun delete(objectKey: String?) {
        if (!StringUtils.hasText(objectKey)) {
            return
        }
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket)
                .key(objectKey)
                .build()
        )
    }

    private fun clientRequiredHeaders(
        presignedRequest: PresignedPutObjectRequest,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        presignedRequest.signedHeaders().forEach { (name, values) ->
            if (!name.equals("host", ignoreCase = true) &&
                !name.equals("content-length", ignoreCase = true) &&
                values.isNotEmpty()
            ) {
                headers[name] = values.first()
            }
        }
        return headers.toMap()
    }

    // 앞뒤 슬래시를 모두 떼던 while 두 개와 결과가 같다.
    private fun normalizedPrefix(): String = properties.keyPrefix.trim().trim('/')

    companion object {
        private const val CACHE_CONTROL = "public, max-age=31536000, immutable"
    }
}
