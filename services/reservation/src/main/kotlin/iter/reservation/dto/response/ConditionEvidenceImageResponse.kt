package iter.reservation.dto.response

import iter.common.image.CaptureView
import kotlin.jvm.JvmRecord

// 수령 또는 반납 사진 한 장의 촬영 방향과 조회 URL입니다.
@JvmRecord
data class ConditionEvidenceImageResponse(val captureView: CaptureView?, val imageUrl: String?)
