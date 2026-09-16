package com.example.iter.ai.service

import com.example.iter.common.exception.CustomException
import com.example.iter.common.storage.S3StorageProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse

class ConditionEvidenceImagesTest {
    private val s3: S3Client = mock()
    private val properties = S3StorageProperties()

    private fun images(): ConditionEvidenceImages {
        properties.bucket = "test-bucket"
        properties.region = "ap-northeast-2"
        return ConditionEvidenceImages(s3, properties)
    }

    @Test
    fun 올바른_증빙만_허용하고_ETag를_고정한다() {
        whenever(s3.headObject(any<HeadObjectRequest>())).thenReturn(headObject("version", 100L))

        assertThat(images().reference(BASE + KEY, "before-0"))
            .containsEntry("etag", "version")
            .containsEntry("objectKey", KEY)
    }

    @Test
    fun V2_각도_경로와_장비_등록_사진을_허용한다() {
        whenever(s3.headObject(any<HeadObjectRequest>())).thenReturn(headObject("version", 100L))
        val uuid = "12345678-1234-1234-1234-123456789abc.jpg"

        assertThat(
            images().reference(
                BASE + "equipment/private/rental-evidence/10/2/receipt/front/" + uuid,
                "before-front",
                "FRONT"
            )
        ).containsEntry("captureSlot", "FRONT")
        assertThat(images().reference(BASE + "equipment/public/20/" + uuid, "listing-front", "FRONT"))
            .containsEntry("captureSlot", "FRONT")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://evil.test/photo.jpg",
            "http://169.254.169.254/latest/meta-data",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/equipment/public/other.jpg",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/equipment/private/rental-evidence/10/2/receipt/../secret",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/equipment/public/rental-evidence/../secret",
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/equipment/public/rental-evidence/%2e%2e/secret"
        ]
    )
    fun 임의_URL과_다른_경로는_S3호출전에_거부한다(url: String) {
        assertThatThrownBy { images().reference(url, "before-0") }
            .isInstanceOf(CustomException::class.java)
        verifyNoInteractions(s3)
    }

    @Test
    fun 서명_쿼리는_허용하지만_조각식별자와_과대파일은_거부한다() {
        whenever(s3.headObject(any<HeadObjectRequest>())).thenReturn(headObject("v1", 100L))
        assertThat(images().reference("$BASE$KEY?X-Amz-Signature=test", "id"))
            .containsEntry("objectKey", KEY)
        assertThatThrownBy { images().reference("$BASE$KEY#x", "id") }
            .isInstanceOf(CustomException::class.java)

        whenever(s3.headObject(any<HeadObjectRequest>())).thenReturn(headObject("v1", 20_000_001L))
        assertThatThrownBy { images().reference(BASE + KEY, "id") }
            .isInstanceOf(CustomException::class.java)
    }

    private fun headObject(etag: String, length: Long) = HeadObjectResponse.builder()
        .eTag(etag)
        .contentType("image/jpeg")
        .contentLength(length)
        .build()

    private companion object {
        const val BASE = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/"
        const val KEY = "equipment/private/rental-evidence/10/2/receipt/12345678-1234-1234-1234-123456789abc.jpg"
    }
}
