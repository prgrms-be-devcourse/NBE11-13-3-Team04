package iter.common.dto.request

import iter.common.image.CaptureView
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kotlin.jvm.JvmRecord

// 최종 제출할 S3 객체 키와 사용자가 선택한 촬영 방향을 한 묶음으로 전달합니다.
@JvmRecord
data class CapturedImageRequest(
    @field:NotNull(message = "사진 촬영 방향은 필수입니다.")
    val captureView: CaptureView?,

    @field:NotBlank(message = "이미지 객체 키는 빈 값일 수 없습니다.")
    val objectKey: String?
)
