package iter.dispute.api

// 반납 분쟁 생성에 필요한 값. 원시값만 담고 엔티티는 담지 않는다.
//
// reason/description 은 non-null 로 선언한다 — 유일한 생성 지점인
// ReturnConfirmationService.createReturnDispute 가 이미 requireNotNull 로 감싸서 넘긴다.
@JvmRecord
data class ReturnDisputeCommand(
    val rentalId: Long,
    val reporterId: Long,
    val respondentId: Long,
    val reason: String,
    val description: String,
)
