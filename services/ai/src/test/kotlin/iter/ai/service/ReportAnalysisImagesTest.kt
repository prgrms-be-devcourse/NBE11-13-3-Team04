package iter.ai.service

import iter.common.storage.S3StorageProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse

class ReportAnalysisImagesTest {
    private val s3: S3Client = mock()
    private val properties = S3StorageProperties()
    private lateinit var images: ReportAnalysisImages

    @BeforeEach
    fun setup() {
        properties.bucket = "test-bucket"
        properties.region = "ap-northeast-2"
        properties.publicBaseUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com"
        images = ReportAnalysisImages(s3, properties)
    }

    @Test
    fun 공개_장비와_비공개_거래_사진을_ETag와_함께_참조한다() {
        whenever(s3.headObject(any<HeadObjectRequest>())).thenReturn(headObject())

        assertThat(images.reference("equipment/public/10/photo.png", "listing-1", "LISTING_1"))
            .hasValueSatisfying { reference ->
                assertThat(reference)
                    .containsEntry("objectKey", "equipment/public/10/photo.png")
                    .containsEntry("etag", "version-1")
            }

        assertThat(
            images.reference(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" +
                    "equipment/private/rental-evidence/20/3/receipt/photo.png?signature=x",
                "receipt-1",
                "RECEIPT_1"
            )
        ).isPresent()
    }

    @Test
    fun 외부_URL과_손상된_객체는_분석에서_제외한다() {
        assertThat(images.reference("https://evil.example/equipment/public/photo.png", "bad", "LISTING_1"))
            .isEmpty()
        verifyNoInteractions(s3)

        whenever(s3.headObject(any<HeadObjectRequest>())).thenThrow(RuntimeException("missing"))
        assertThat(images.reference("equipment/public/10/missing.png", "missing", "LISTING_1"))
            .isEmpty()
    }

    private fun headObject() = HeadObjectResponse.builder()
        .eTag("version-1")
        .contentType("image/png")
        .contentLength(1_024L)
        .build()
}
