package iter.device.api

import java.math.BigDecimal

// 다른 도메인이 장비를 가리킬 때 쓰는 최소 정보.
//
// !! EquipmentStatus 를 노출하지 않는다 !!
// 소비자 2곳이 실제로 쓰는 건 "== DELETED" 비교와 isActive() 뿐이라
// 불리언으로 낮춰도 정보 손실이 없고, 열거형 크로스 참조가 타입 이동 없이 사라진다.
// 상태가 늘어나도 다른 도메인은 영향을 받지 않는다.
//
// categoryName 을 String 으로 두는 것도 같은 이유다 —
// 유일한 소비자(RentalService)가 쓰는 건 category.name() 한 곳이다.
//
// 빌더를 두지 않는 이유는 auth/api/UserProfile 주석 참고.
@JvmRecord
data class EquipmentInfo(
    val equipmentId: Long,
    // ownerId 가 null 이면 isOwnedBy 가 전부 false 로 떨어져 소유권 검증이 조용히 통과한다.
    val ownerId: Long,
    val name: String,
    val categoryName: String,
    val dailyPrice: BigDecimal,
    val active: Boolean,
    val deleted: Boolean,
) {
    fun isOwnedBy(userId: Long): Boolean = ownerId == userId
    fun isActive(): Boolean = active
}
