package iter.device.service

import iter.auth.api.UserQueryPort
import iter.common.dto.request.CapturedImageRequest
import iter.common.exception.CustomException
import iter.common.exception.ErrorCode
import iter.common.image.CaptureView
import iter.common.security.AuthUser
import iter.common.security.UserStatus
import iter.device.domain.entity.Equipment
import iter.device.domain.entity.EquipmentImage
import iter.device.domain.entity.EquipmentImageUpload
import iter.device.domain.entity.EquipmentStatus
import iter.device.domain.entity.ProductConditionType
import iter.device.domain.repository.EquipmentImageRepository
import iter.device.domain.repository.EquipmentImageUploadRepository
import iter.device.domain.repository.EquipmentRepository
import iter.device.dto.request.EquipmentCreateRequest
import iter.device.dto.request.EquipmentImageCreateRequest
import iter.device.dto.request.EquipmentStatusUpdateRequest
import iter.device.dto.request.EquipmentUpdateRequest
import iter.device.dto.response.EquipmentDetailResponse
import iter.device.dto.response.EquipmentImageResponse
import iter.device.dto.response.EquipmentOwnerResponse
import iter.device.dto.response.EquipmentStatusResponse
import iter.device.storage.EquipmentImageStorage
import iter.device.storage.StoredImage
import iter.device.storage.ValidatedUpload
import iter.device.support.EquipmentImageUrlResolver
import iter.reservation.api.RentalQueryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

import java.time.LocalDate
import java.time.LocalDateTime

@Service
class EquipmentManagementService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentImageRepository: EquipmentImageRepository,
    private val imageUploadRepository: EquipmentImageUploadRepository,
    private val userQueryPort: UserQueryPort,
    private val rentalQueryPort: RentalQueryPort,
    private val imageStorage: EquipmentImageStorage,
    private val imageCleanupService: EquipmentImageCleanupService,
    private val imageUrlResolver: EquipmentImageUrlResolver,
) {

    @Transactional
    fun create(owner: AuthUser, request: EquipmentCreateRequest): EquipmentDetailResponse {
        validateCanCreate(owner)
        val imageKeys = request.imageKeys!!
        val uploadRecords = findAndValidateUploadRecords(owner.id, imageKeys)
        validateCaptureViews(request.orderedImages(), uploadRecords)
        val validatedUploads = uploadRecords.map { upload ->
            imageStorage.validateTemporaryUpload(upload.objectKey, upload.expectedContentType, upload.expectedSize)
        }
        val productCondition = request.productCondition!!

        val equipment = equipmentRepository.saveAndFlush(
            Equipment(
                owner.id,
                request.category!!,
                request.name!!.trim(),
                request.description!!.trim(),
                request.dailyPrice!!,
                request.availableFrom,
                request.availableTo,
                EquipmentStatus.ACTIVE,
                productCondition,
                normalizeConditionDetail(productCondition, request.conditionDetail),
            )
        )
        val equipmentId = equipment.id!!

        val storedImages = promoteAll(equipmentId, validatedUploads)
        registerStorageSynchronization(storedImages, imageKeys)
        val usedAt = LocalDateTime.now()
        uploadRecords.forEach { it.use(usedAt) }

        val orderedImages = request.orderedImages()
        storedImages.forEachIndexed { index, storedImage ->
            val capturedImage = orderedImages[index]
            equipmentImageRepository.save(
                EquipmentImage(
                    equipment,
                    storedImage.imageUrl,
                    storedImage.objectKey,
                    index,
                    capturedImage.captureView == CaptureView.FRONT,
                    capturedImage.captureView,
                )
            )
        }

        equipmentImageRepository.flush()
        log.info("장비 등록 처리: equipmentId={}, ownerId={}, imageCount={}", equipmentId, owner.id, storedImages.size)
        return toDetailResponse(equipmentId)
    }

    @Transactional
    fun addImages(
        owner: AuthUser,
        equipmentId: Long,
        request: EquipmentImageCreateRequest,
    ): List<EquipmentImageResponse> {
        validateCanCreate(owner)
        val equipment = findOwnedForUpdate(
            equipmentId, owner.id, "본인 소유의 장비에만 이미지를 추가할 수 있습니다.")
        val existingImages = equipmentImageRepository
            .findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
        val imageKeys = request.imageKeys!!
        if (existingImages.size + imageKeys.size > EquipmentImagePolicy.MAX_IMAGE_COUNT) {
            throw CustomException(ErrorCode.IMAGE_LIMIT_EXCEEDED)
        }

        val uploadRecords = findAndValidateUploadRecords(owner.id, imageKeys)
        val validatedUploads = uploadRecords.map { upload ->
            imageStorage.validateTemporaryUpload(upload.objectKey, upload.expectedContentType, upload.expectedSize)
        }
        val storedImages = promoteAll(equipmentId, validatedUploads)
        registerStorageSynchronization(storedImages, imageKeys)

        val thumbnailIndex = request.thumbnailIndex
        if (thumbnailIndex != null) {
            existingImages.forEach { it.changeThumbnail(false) }
        }
        val nextSortOrder = (existingImages.maxOfOrNull { it.sortOrder } ?: -1) + 1
        storedImages.forEachIndexed { index, storedImage ->
            equipmentImageRepository.save(
                EquipmentImage(
                    equipment,
                    storedImage.imageUrl,
                    storedImage.objectKey,
                    nextSortOrder + index,
                    thumbnailIndex != null && index == thumbnailIndex,
                )
            )
        }
        val usedAt = LocalDateTime.now()
        uploadRecords.forEach { it.use(usedAt) }
        equipmentImageRepository.flush()
        log.info("장비 이미지 추가 처리: equipmentId={}, ownerId={}, addedCount={}",
            equipmentId, owner.id, storedImages.size)

        return equipmentImageRepository.findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
            .map { image -> EquipmentImageResponse.from(image, imageUrlResolver.resolve(image)) }
    }

    @Transactional
    fun deleteImage(ownerId: Long, equipmentId: Long, imageId: Long) {
        findOwnedForUpdate(equipmentId, ownerId, "본인 소유의 장비 이미지만 삭제할 수 있습니다.")
        val images = equipmentImageRepository.findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
        val target = images.find { it.id == imageId }
            ?: throw CustomException(ErrorCode.EQUIPMENT_IMAGE_NOT_FOUND)
        if (images.size <= EquipmentImagePolicy.MIN_IMAGE_COUNT) {
            throw CustomException(ErrorCode.MINIMUM_IMAGE_REQUIRED)
        }

        val remainingImages = images.filter { it.id != imageId }
        if (target.thumbnail) {
            remainingImages.first().changeThumbnail(true)
        }
        remainingImages.forEachIndexed { index, image -> image.changeSortOrder(index) }
        equipmentImageRepository.delete(target)
        equipmentImageRepository.flush()
        registerDeleteAfterCommit(target.objectKey)
        log.info("장비 이미지 삭제 처리: equipmentId={}, ownerId={}, imageId={}",
            equipmentId, ownerId, imageId)
    }

    @Transactional
    fun update(ownerId: Long, equipmentId: Long, request: EquipmentUpdateRequest): EquipmentDetailResponse {
        val equipment = findOwnedForUpdate(equipmentId, ownerId, "본인 소유의 장비만 수정할 수 있습니다.")

        val name = request.name?.trim() ?: equipment.name
        val description = request.description?.trim() ?: equipment.description
        val dailyPrice = request.dailyPrice ?: equipment.dailyPrice
        val availableFrom = request.availableFrom ?: equipment.availableFrom
        val availableTo = request.availableTo ?: equipment.availableTo
        val productCondition = request.productCondition ?: equipment.productCondition
        val conditionDetail = request.conditionDetail?.trim() ?: equipment.conditionDetail

        validateUpdateValues(availableFrom, availableTo, productCondition, conditionDetail)
        validateExistingRentalsRemainIncluded(equipment, availableFrom, availableTo)

        equipment.update(
            name,
            description,
            dailyPrice,
            availableFrom,
            availableTo,
            productCondition,
            normalizeConditionDetail(productCondition, conditionDetail),
        )
        equipmentRepository.flush()
        log.info("장비 정보 변경 처리: equipmentId={}, ownerId={}", equipmentId, ownerId)
        return toDetailResponse(equipmentId)
    }

    @Transactional
    fun delete(ownerId: Long, equipmentId: Long) {
        val equipment = findOwnedForUpdate(equipmentId, ownerId, "본인 소유의 장비만 삭제할 수 있습니다.")

        // 어떤 상태가 분쟁·삭제 차단에 해당하는지는 reservation 이 판단한다.
        if (rentalQueryPort.hasDisputedRental(equipmentId)) {
            throw CustomException(ErrorCode.ACTIVE_DISPUTE_EXISTS)
        }
        if (rentalQueryPort.hasDeletionBlockingRental(equipmentId)) {
            throw CustomException(ErrorCode.ACTIVE_RENTAL_EXISTS)
        }

        equipment.delete()
        equipmentRepository.flush()
        log.info("장비 삭제 처리: equipmentId={}, ownerId={}", equipmentId, ownerId)
    }

    @Transactional
    fun updateStatus(
        owner: AuthUser,
        equipmentId: Long,
        request: EquipmentStatusUpdateRequest,
    ): EquipmentStatusResponse {
        val equipment = findOwnedForUpdate(
            equipmentId, owner.id, "해당 장비의 상태를 변경할 권한이 없습니다.")

        val requestedStatus = request.status!!
        if (equipment.status == EquipmentStatus.SUSPENDED ||
            equipment.status == EquipmentStatus.DELETED ||
            equipment.status == requestedStatus
        ) {
            throw CustomException(ErrorCode.EQUIPMENT_STATUS_CHANGE_NOT_ALLOWED)
        }
        if (requestedStatus == EquipmentStatus.ACTIVE) {
            validateCanCreate(owner)
        }

        val previousStatus = equipment.status
        equipment.changeStatus(requestedStatus)
        equipmentRepository.flush()
        log.info("장비 상태 변경 처리: equipmentId={}, ownerId={}, previousStatus={}, status={}",
            equipmentId, owner.id, previousStatus, equipment.status)
        return EquipmentStatusResponse(equipment.id!!, equipment.status, equipment.updatedAt)
    }

    private fun findOwnedForUpdate(equipmentId: Long, ownerId: Long, forbiddenMessage: String): Equipment {
        val equipment = equipmentRepository.findByIdForUpdate(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND, "존재하지 않는 장비입니다.") }
        if (equipment.status == EquipmentStatus.DELETED) {
            throw CustomException(ErrorCode.EQUIPMENT_NOT_FOUND, "존재하지 않는 장비입니다.")
        }
        if (!equipment.isOwnedBy(ownerId)) {
            throw CustomException(ErrorCode.FORBIDDEN, forbiddenMessage)
        }
        return equipment
    }

    private fun validateCanCreate(owner: AuthUser) {
        if (owner.status == UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.USER_SUSPENDED)
        }
        if (owner.status == UserStatus.DELETED) {
            throw CustomException(ErrorCode.USER_DELETED)
        }
    }

    private fun validateUpdateValues(
        availableFrom: LocalDate?,
        availableTo: LocalDate?,
        productCondition: ProductConditionType,
        conditionDetail: String?,
    ) {
        if (availableFrom == null ||
            availableTo == null ||
            availableTo.isBefore(availableFrom) ||
            productCondition != ProductConditionType.NORMAL && (conditionDetail == null || conditionDetail.isBlank())
        ) {
            throw CustomException(
                ErrorCode.VALIDATION_ERROR,
                "장비 수정 정보를 올바르게 입력해주세요.",
            )
        }
    }

    private fun validateExistingRentalsRemainIncluded(
        equipment: Equipment,
        availableFrom: LocalDate?,
        availableTo: LocalDate?,
    ) {
        val periodChanged = availableFrom != equipment.availableFrom || availableTo != equipment.availableTo
        if (periodChanged &&
            rentalQueryPort.hasOccupyingRentalOutsidePeriod(equipment.id!!, availableFrom!!, availableTo!!)
        ) {
            throw CustomException(
                ErrorCode.ACTIVE_RENTAL_EXISTS,
                "기존 예약을 제외하는 기간으로 대여 가능 기간을 변경할 수 없습니다.",
            )
        }
    }

    private fun normalizeConditionDetail(
        productCondition: ProductConditionType,
        conditionDetail: String?,
    ): String? {
        if (productCondition == ProductConditionType.NORMAL) {
            return null
        }
        return conditionDetail?.trim()
    }

    private fun toDetailResponse(equipmentId: Long): EquipmentDetailResponse {
        val equipment = equipmentRepository.findById(equipmentId)
            .orElseThrow { CustomException(ErrorCode.EQUIPMENT_NOT_FOUND) }
        val owner = userQueryPort.findSummary(equipment.ownerId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val images = equipmentImageRepository
            .findByEquipmentIdOrderBySortOrderAscIdAsc(equipmentId)
            .map { image -> EquipmentImageResponse.from(image, imageUrlResolver.resolve(image)) }

        return EquipmentDetailResponse(
            equipment.id!!,
            equipment.name,
            equipment.category,
            equipment.description,
            equipment.dailyPrice,
            equipment.availableFrom,
            equipment.availableTo,
            equipment.status,
            equipment.productCondition,
            equipment.conditionDetail,
            images,
            EquipmentOwnerResponse(owner.userId, owner.nickName),
            0.0,
            0L,
            equipment.createdAt,
        )
    }

    private fun findAndValidateUploadRecords(
        ownerId: Long,
        objectKeys: List<String>,
    ): List<EquipmentImageUpload> {
        val records = imageUploadRepository.findAllByObjectKeyInForUpdate(objectKeys)
        val recordsByKey = LinkedHashMap<String, EquipmentImageUpload>()
        records.forEach { recordsByKey[it.objectKey] = it }

        val now = LocalDateTime.now()
        return objectKeys.map { objectKey ->
            val record = recordsByKey[objectKey]
            if (record == null || record.userId != ownerId) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND)
            }
            if (record.isUsed()) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_ALREADY_USED)
            }
            if (record.isExpired(now)) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_EXPIRED)
            }
            record
        }
    }

    // 최종 제출한 촬영 방향이 presigned URL 발급 시 지정한 방향과 같은지 확인합니다.
    private fun validateCaptureViews(
        images: List<CapturedImageRequest>,
        uploads: List<EquipmentImageUpload>,
    ) {
        val uploadsByKey = uploads.associateBy { it.objectKey }
        images.forEach { image ->
            val upload = uploadsByKey[image.objectKey]
            if (upload == null || upload.captureView != image.captureView) {
                throw CustomException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND)
            }
        }
    }

    private fun promoteAll(equipmentId: Long, validatedUploads: List<ValidatedUpload>): List<StoredImage> {
        val promoted = ArrayList<StoredImage>()
        try {
            validatedUploads.forEach { promoted.add(imageStorage.promote(equipmentId, it)) }
            return promoted.toList()
        } catch (exception: RuntimeException) {
            promoted.forEach { deleteQuietly(it.objectKey, "부분 승격된 장비 이미지 삭제 실패") }
            throw exception
        }
    }

    private fun registerStorageSynchronization(
        storedImages: List<StoredImage>,
        temporaryObjectKeys: List<String>,
    ) {
        val temporaryKeys = temporaryObjectKeys.toList()
        val storedKeys = storedImages.map { it.objectKey }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                imageCleanupService.deleteAll(temporaryKeys, "사용 완료된 임시 장비 이미지 삭제 실패")
            }

            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return
                }
                imageCleanupService.deleteAll(storedKeys, "롤백된 최종 장비 이미지 삭제 실패")
            }
        })
    }

    private fun registerDeleteAfterCommit(objectKey: String?) {
        if (objectKey == null || objectKey.isBlank()) {
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                imageCleanupService.deleteAll(listOf(objectKey), "삭제된 장비 이미지 객체 정리 실패")
            }
        })
    }

    // 승격 도중 실패한 객체는 요청 실패 전에 즉시 보상 삭제해야 하므로 동기로 처리합니다.
    private fun deleteQuietly(objectKey: String, failureMessage: String) {
        try {
            imageStorage.delete(objectKey)
        } catch (exception: RuntimeException) {
            log.error("{}: objectKey={}", failureMessage, objectKey, exception)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EquipmentManagementService::class.java)
    }
}
