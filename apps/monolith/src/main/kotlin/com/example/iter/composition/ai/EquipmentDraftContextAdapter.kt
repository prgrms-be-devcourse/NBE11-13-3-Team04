package com.example.iter.composition.ai

import com.example.iter.ai.port.DraftImageReference
import com.example.iter.ai.port.EquipmentDraftContextPort
import com.example.iter.auth.domain.entity.User
import com.example.iter.auth.domain.repository.UserRepository
import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.UserStatus
import com.example.iter.device.domain.entity.EquipmentImageUpload
import com.example.iter.device.domain.repository.EquipmentImageUploadRepository
import com.example.iter.device.storage.EquipmentImageStorage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// AI 모듈이 auth·device 내부 구현을 직접 참조하지 않도록 모놀리스 조합 계층에서 연결합니다.
@Component
class EquipmentDraftContextAdapter(
    private val users: UserRepository,
    private val uploads: EquipmentImageUploadRepository,
    private val imageStorage: EquipmentImageStorage,
    private val clock: Clock
) : EquipmentDraftContextPort {
    override fun loadValidatedImages(ownerId: Long, objectKeys: List<String>): List<DraftImageReference> {
        requireActiveOwner(ownerId)

        val orderedUploads = validateUploads(ownerId, objectKeys)

        // S3 HEAD 요청은 호출 트랜잭션 밖에서 실행해 DB 락을 오래 점유하지 않습니다.
        return orderedUploads.map { upload ->
            val validated = imageStorage.validateTemporaryUpload(
                upload.objectKey,
                upload.expectedContentType,
                upload.expectedSize
            )

            DraftImageReference(
                imageId = requireNotNull(upload.id).toString(),
                objectKey = validated.objectKey,
                etag = validated.eTag,
                captureSlot = upload.captureView?.name ?: "OVERVIEW",
                contentType = validated.contentType,
                sizeBytes = validated.size
            )
        }
    }

    override fun requireActiveOwner(ownerId: Long) {
        val owner = users.findById(ownerId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        validateActive(owner)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockOwnerAndValidateUploads(ownerId: Long, objectKeys: List<String>) {
        // 일일 한도 확인과 작업 저장 사이에 회원 상태가 바뀌지 않도록 회원 행을 잠급니다.
        val owner = users.findWithLockById(ownerId).orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        validateActive(owner)
        validateUploads(ownerId, objectKeys)
    }

    // 요청 순서를 보존하면서 모든 업로드가 본인 소유·미사용·유효 기간 내인지 확인합니다.
    private fun validateUploads(ownerId: Long, objectKeys: List<String>): List<EquipmentImageUpload> {
        if (objectKeys.isEmpty() || objectKeys.size > MAX_IMAGES || objectKeys.toSet().size != objectKeys.size) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }

        val records = uploads.findAllByObjectKeyIn(objectKeys)

        if (records.size != objectKeys.size) {
            throw CustomException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND)
        }

        // IN 조회 결과 순서는 보장되지 않으므로 objectKey로 다시 매핑해 요청 순서대로 복원합니다.
        val byObjectKey = records.associateBy { upload -> upload.objectKey }
        val now = LocalDateTime.now(clock)

        return objectKeys.map { objectKey ->
            val upload = byObjectKey[objectKey] ?: throw CustomException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND)

            if (upload.userId != ownerId) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND)
            }

            if (upload.isUsed()) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_ALREADY_USED)
            }

            if (upload.isExpired(now)) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_EXPIRED)
            }

            upload
        }
    }

    private fun validateActive(user: User) {
        when (user.status) {
            UserStatus.SUSPENDED -> throw CustomException(ErrorCode.USER_SUSPENDED)
            UserStatus.DELETED -> throw CustomException(ErrorCode.USER_DELETED)
            UserStatus.ACTIVE -> Unit
        }
    }

    private companion object {
        private const val MAX_IMAGES = 5
    }
}
