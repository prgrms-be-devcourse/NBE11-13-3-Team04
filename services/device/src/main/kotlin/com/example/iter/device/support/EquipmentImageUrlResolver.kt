package com.example.iter.device.support

import com.example.iter.common.storage.S3StorageProperties
import com.example.iter.device.domain.entity.EquipmentImage
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetUrlRequest

@Component
class EquipmentImageUrlResolver(
    private val s3Client: S3Client,
    private val properties: S3StorageProperties,
) {

    fun resolve(image: EquipmentImage): String {
        if (!StringUtils.hasText(image.objectKey)) {
            return image.imageUrl
        }
        return resolve(image.objectKey!!)
    }

    fun resolve(objectKey: String): String {
        if (StringUtils.hasText(properties.publicBaseUrl)) {
            return properties.publicBaseUrl!!.trimEnd('/') + "/" + objectKey
        }
        return s3Client.utilities()
            .getUrl(
                GetUrlRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .build()
            )
            .toExternalForm()
    }
}
