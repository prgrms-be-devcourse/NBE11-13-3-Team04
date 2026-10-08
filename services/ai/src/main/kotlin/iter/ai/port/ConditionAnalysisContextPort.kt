package iter.ai.port

data class ConditionImage(val captureView: String?, val imageUrl: String?)

data class ConditionComparisonContext(
    val listingImages: List<ConditionImage>,
    val beforeImages: List<ConditionImage>,
    val afterImages: List<ConditionImage>
)

// AI 모듈이 회원·대여·반납 구현을 직접 참조하지 않고 필요한 검증과 비교 자료만 요청하는 출력 Port입니다.
interface ConditionAnalysisContextPort {
    // 저장된 AI 작업을 조회할 때 요청자가 거래 생성 당시 등록자인지 확인합니다.
    fun requireOwner(ownerId: Long, rentalId: Long)

    // 외부 이미지 조회 전에 거래가 반납 비교 가능한 상태인지 가볍게 확인합니다.
    fun requireReady(ownerId: Long, rentalId: Long)

    // 호출 트랜잭션 안에서 회원·대여 행을 잠그고 분석 가능 상태를 재검증합니다.
    fun lockAndRequireReady(ownerId: Long, rentalId: Long)

    // 등록·수령·반납 사진을 촬영 방향과 함께 불러와 AI 입력용 자료로 반환합니다.
    fun loadComparison(ownerId: Long, rentalId: Long): ConditionComparisonContext
}
