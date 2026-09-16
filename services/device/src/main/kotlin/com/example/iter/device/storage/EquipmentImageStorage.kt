package com.example.iter.device.storage

interface EquipmentImageStorage {

    fun createPresignedUpload(
        userId: Long,
        contentType: String,
        expectedSize: Long,
    ): PresignedUpload

    fun validateTemporaryUpload(
        objectKey: String,
        expectedContentType: String,
        expectedSize: Long,
    ): ValidatedUpload

    fun promote(equipmentId: Long, upload: ValidatedUpload): StoredImage

    // objectKey 가 nullable 이다. EquipmentImage.objectKey 가 nullable 이고,
    // 구현이 빈 값이면 아무 것도 안 하고 돌아가는 계약이다.
    fun delete(objectKey: String?)
}
