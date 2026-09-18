package iter.reservation.dto.request

import iter.common.image.CaptureView
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

// 수령/반납 증빙 사진 업로드용 presigned URL 요청 — 장비 이미지(device 도메인)와 달리
// temp->public 승격 단계가 없어 훨씬 단순하다: 발급받은 objectKey가 곧 최종 위치다.
data class EvidenceImagePresignRequest(
    @field:NotEmpty(message = "이미지는 최소 1장 필요합니다.")
    @field:Size(max = 4, message = "이미지는 최대 4장까지 업로드할 수 있습니다.")
    val files: List<@Valid Item>?,
) {
    fun files(): List<Item>? = files

    data class Item(
        @field:NotNull(message = "사진 촬영 방향은 필수입니다.")
        val captureView: CaptureView?,

        @field:NotBlank(message = "Content-Type은 필수입니다.")
        @field:Pattern(
            regexp = "image/jpeg|image/png|image/webp",
            message = "JPEG, PNG, WebP 형식만 지원합니다.",
        )
        val contentType: String?,

        @field:Positive(message = "이미지 크기는 0보다 커야 합니다.")
        @field:Max(value = 10_485_760, message = "이미지는 한 장당 10MB 이하여야 합니다.")
        val size: Long,
    ) {
        fun captureView(): CaptureView? = captureView
        fun contentType(): String? = contentType
        fun size(): Long = size
    }
}
