package com.example.iter.device.service

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import com.example.iter.common.security.AuthUser
import com.example.iter.common.security.UserStatus
import com.example.iter.device.domain.entity.EquipmentImageUpload
import com.example.iter.device.domain.repository.EquipmentImageUploadRepository
import com.example.iter.device.dto.request.PresignedImageUploadRequest
import com.example.iter.device.dto.response.PresignedImageUploadItemResponse
import com.example.iter.device.dto.response.PresignedImageUploadResponse
import com.example.iter.device.storage.EquipmentImageStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EquipmentImageUploadService(
    private val uploadRepository: EquipmentImageUploadRepository,
    private val imageStorage: EquipmentImageStorage,
    private val imagePolicy: EquipmentImagePolicy,
) {

    @Transactional
    fun issue(user: AuthUser, request: PresignedImageUploadRequest): PresignedImageUploadResponse {
        validateActiveUser(user)

        // files 는 @field:NotNull 로 검증되므로 여기 도달했다면 null 이 아니다.
        // 자바에서도 null 이면 getFiles().stream() 에서 NPE 였다.
        val uploads = request.files!!.map { file ->
            imagePolicy.validateMetadata(file.contentType, file.size)
            val presigned = imageStorage.createPresignedUpload(
                user.id, file.contentType, file.size,
            )
            uploadRepository.save(
                EquipmentImageUpload(
                    user.id,
                    presigned.objectKey,
                    file.contentType,
                    file.size,
                    presigned.expiresAt,
                )
            )
            PresignedImageUploadItemResponse(
                presigned.objectKey,
                presigned.uploadUrl.toExternalForm(),
                presigned.requiredHeaders,
                presigned.expiresAt,
            )
        }
        return PresignedImageUploadResponse(uploads)
    }

    private fun validateActiveUser(user: AuthUser) {
        if (user.status == UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.USER_SUSPENDED)
        }
        if (user.status == UserStatus.DELETED) {
            throw CustomException(ErrorCode.USER_DELETED)
        }
    }
}
