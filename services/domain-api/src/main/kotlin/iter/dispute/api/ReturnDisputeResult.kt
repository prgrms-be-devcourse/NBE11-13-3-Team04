package iter.dispute.api

// 비정상 반납 처리에서 함께 생성된 분쟁과 관리자 신고의 식별자입니다.
@JvmRecord
data class ReturnDisputeResult(
    val disputeId: Long,
    val reportId: Long,
)
