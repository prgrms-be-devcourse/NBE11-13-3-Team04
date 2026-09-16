package com.example.iter.reservation.service

import com.example.iter.common.dto.request.CapturedImageRequest
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.image.CaptureView
import com.example.iter.common.storage.S3StorageProperties
import com.example.iter.reservation.api.RentalStatus
import com.example.iter.reservation.domain.entity.EvidenceUploadPhase
import com.example.iter.reservation.domain.entity.RentalEvidenceUpload
import com.example.iter.reservation.domain.repository.RentalEvidenceUploadRepository
import com.example.iter.reservation.domain.repository.RentalRepository
import com.example.iter.reservation.dto.request.EvidenceImagePresignRequest
import com.example.iter.reservation.dto.response.EvidenceImagePresignResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

import java.time.LocalDateTime
import java.util.UUID

// 수령·반납 증빙 사진의 비공개 S3 업로드 권한과 조회용 임시 URL을 관리합니다.
@Service
class RentalEvidenceUploadService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3StorageProperties,
    private val rentalRepository: RentalRepository,
    private val uploadRepository: RentalEvidenceUploadRepository,
) {

    // 로그인한 대여자와 거래 상태를 확인한 뒤 현재 단계 전용 업로드 URL을 발급합니다.
    // MockMvc 테스트가 @MockitoBean 으로 이 서비스를 목킹하며 verify(..., never())
    // 처럼 타입 지정 없는 any()를 쓴다. non-null Long으로 두면 JVM 시그니처가 primitive
    // long이 되어 언박싱 NPE가 나므로(device/reservation의 다른 서비스와 동일한 함정)
    // userId·rentalId는 Long?로 받고 메서드 본문 진입 직후 !!로 푼다.
    @Transactional
    fun createPresignedUploads(
        userId: Long?,
        rentalId: Long?,
        request: EvidenceImagePresignRequest,
    ): EvidenceImagePresignResponse {
        val userId = userId!!
        val rentalId = rentalId!!
        val rental = rentalRepository.findWithLockById(rentalId)
            .orElseThrow { CustomException(ErrorCode.RENTAL_NOT_FOUND) }

        if (!rental.isRenter(userId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }

        val phase = phaseFor(rental.status)
        val files = request.files!!
        validateIssuanceLimit(rentalId, userId, phase, files.size)

        val expiresAt = LocalDateTime.now().plus(properties.presignedUrlValidity)
        val items = files.map { file ->
            validateMetadata(file)
            presignOne(userId, rentalId, phase, file, expiresAt)
        }

        return EvidenceImagePresignResponse(items)
    }

    // 제출된 키가 이 대여와 사용자에게 발급됐고 실제 S3 업로드까지 끝났는지 검사한 뒤 사용 처리합니다.
    @Transactional
    fun validateAndUse(
        userId: Long,
        rentalId: Long,
        phase: EvidenceUploadPhase,
        images: List<CapturedImageRequest>?,
    ) {
        if (!hasRequiredViews(images)) {
            throw CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID)
        }

        val objectKeys = images!!.map { requireNotNull(it.objectKey) }
        val uploadsByKey = uploadRepository.findAllByObjectKeyInForUpdate(objectKeys)
            .associateBy { it.objectKey }

        images.forEach { image ->
            val upload = uploadsByKey[image.objectKey]
            validateUploadScope(upload, userId, rentalId, phase, image.captureView)
            validateUploadedObject(upload!!)
            upload.use(LocalDateTime.now())
        }
    }

    // DB에는 비공개 object key만 저장하고, 화면 조회 시점에 짧게 유효한 GET URL을 만듭니다.
    fun createReadUrl(storedImageReference: String?): String? {
        val privatePrefix = "${normalizedPrefix()}/$PRIVATE_EVIDENCE_DIR/"
        if (storedImageReference == null || !storedImageReference.startsWith(privatePrefix)) {
            // 기존 공개 URL 데이터는 단계적 마이그레이션 동안 그대로 읽을 수 있게 유지합니다.
            return storedImageReference
        }

        val getObjectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(storedImageReference)
            .build()

        return s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlValidity)
                .getObjectRequest(getObjectRequest)
                .build(),
        ).url().toString()
    }

    // 객체마다 고유한 비공개 키를 만들고 Content-Type, 길이, 덮어쓰기 방지 조건을 서명합니다.
    private fun presignOne(
        userId: Long,
        rentalId: Long,
        phase: EvidenceUploadPhase,
        file: EvidenceImagePresignRequest.Item,
        expiresAt: LocalDateTime,
    ): EvidenceImagePresignResponse.Item {
        val captureView = file.captureView!!
        val contentType = file.contentType!!
        val objectKey = "${normalizedPrefix()}/$PRIVATE_EVIDENCE_DIR/$rentalId/$userId/" +
            "${phase.name.lowercase()}/${captureView.name.lowercase()}/" +
            "${UUID.randomUUID()}.${extensionFor(contentType)}"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(objectKey)
            .contentType(contentType)
            .contentLength(file.size)
            .ifNoneMatch("*")
            .build()

        val uploadUrl = s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlValidity)
                .putObjectRequest(putObjectRequest)
                .build(),
        ).url().toString()

        uploadRepository.save(
            RentalEvidenceUpload(
                rentalId,
                userId,
                phase,
                objectKey,
                captureView,
                contentType,
                file.size,
                expiresAt,
            ),
        )

        return EvidenceImagePresignResponse.Item(
            captureView,
            objectKey,
            uploadUrl,
            mapOf("Content-Type" to contentType, "If-None-Match" to "*"),
            createReadUrl(objectKey)!!,
            expiresAt,
        )
    }

    private fun validateIssuanceLimit(
        rentalId: Long,
        userId: Long,
        phase: EvidenceUploadPhase,
        requestedCount: Int,
    ) {
        val issuedCount = uploadRepository.countByRentalIdAndUserIdAndPhase(rentalId, userId, phase)
        if (issuedCount + requestedCount > MAX_UPLOADS_PER_PHASE) {
            throw CustomException(ErrorCode.EVIDENCE_UPLOAD_LIMIT_EXCEEDED)
        }
    }

    private fun phaseFor(status: RentalStatus): EvidenceUploadPhase =
        when (status) {
            RentalStatus.SHIPPING -> EvidenceUploadPhase.RECEIPT
            RentalStatus.RETURN_REQUESTED -> EvidenceUploadPhase.RETURN
            else -> throw CustomException(ErrorCode.EVIDENCE_UPLOAD_NOT_ALLOWED)
        }

    private fun validateMetadata(file: EvidenceImagePresignRequest.Item) {
        val allowedType = file.contentType in setOf("image/jpeg", "image/png", "image/webp")
        if (!allowedType || file.size <= 0 || file.size > MAX_IMAGE_SIZE) {
            throw CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID)
        }
    }

    private fun validateUploadScope(
        upload: RentalEvidenceUpload?,
        userId: Long,
        rentalId: Long,
        phase: EvidenceUploadPhase,
        captureView: CaptureView?,
    ) {
        if (upload == null ||
            upload.userId != userId ||
            upload.rentalId != rentalId ||
            upload.phase != phase ||
            upload.captureView != captureView
        ) {
            throw CustomException(ErrorCode.EVIDENCE_UPLOAD_NOT_FOUND)
        }
        if (upload.isUsed()) {
            throw CustomException(ErrorCode.EVIDENCE_UPLOAD_ALREADY_USED)
        }
    }

    private fun hasRequiredViews(images: List<CapturedImageRequest>?): Boolean {
        if (images == null || images.size != REQUIRED_IMAGES_PER_SUBMISSION) {
            return false
        }
        val views = images.mapNotNull { it.captureView }.toSet()
        val keys = images.map { it.objectKey }.toSet()
        return views == CaptureView.entries.toSet() && keys.size == images.size
    }

    // S3 HEAD 결과를 발급 당시 메타데이터와 비교해 미업로드·변조된 객체 제출을 막습니다.
    private fun validateUploadedObject(upload: RentalEvidenceUpload) {
        try {
            val metadata = s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(upload.objectKey)
                    .build(),
            )

            if (metadata.contentLength() != upload.expectedSize ||
                upload.expectedContentType != metadata.contentType()
            ) {
                throw CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID)
            }
        } catch (exception: CustomException) {
            throw exception
        } catch (exception: S3Exception) {
            if (exception.statusCode() == 404) {
                throw CustomException(ErrorCode.EVIDENCE_UPLOAD_NOT_FOUND)
            }
            throw CustomException(ErrorCode.EVIDENCE_UPLOAD_FAILED)
        } catch (exception: RuntimeException) {
            throw CustomException(ErrorCode.EVIDENCE_UPLOAD_FAILED)
        }
    }

    private fun extensionFor(contentType: String): String =
        when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID)
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

    companion object {
        const val REQUIRED_IMAGES_PER_SUBMISSION = 3
        const val MAX_UPLOADS_PER_PHASE = 9
        const val MAX_IMAGE_SIZE = 10L * 1024 * 1024
        private const val PRIVATE_EVIDENCE_DIR = "private/rental-evidence"
    }
}
