package com.example.iter.device.service

import com.example.iter.device.config.DeviceAsyncConfig
import com.example.iter.device.storage.EquipmentImageStorage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EquipmentImageCleanupService(
    private val imageStorage: EquipmentImageStorage,
) {

    @Async(DeviceAsyncConfig.S3_TASK_EXECUTOR)
    fun deleteAll(objectKeys: List<String?>, failureMessage: String) {
        objectKeys
            .filter { !it.isNullOrBlank() }
            .forEach { deleteQuietly(it, failureMessage) }
    }

    private fun deleteQuietly(objectKey: String?, failureMessage: String) {
        try {
            imageStorage.delete(objectKey)
        } catch (exception: RuntimeException) {
            log.error("{}: objectKey={}", failureMessage, objectKey, exception)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(EquipmentImageCleanupService::class.java)
    }
}
