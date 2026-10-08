package iter.reservation.api

import java.math.BigDecimal
import java.time.LocalDate

// 다른 도메인이 대여 건을 가리킬 때 쓰는 최소 정보.
//
// 상품명은 Rental 이 대여 시점에 찍어둔 스냅샷(productNameSnapshot)이다.
// 장비가 나중에 이름을 바꾸거나 삭제돼도 알림·영수증에는 당시 이름이 남아야 하므로,
// 여기서 device 에 다시 물어보면 안 된다.
//
// status 를 싣는 이유: 결제 가능 여부처럼 "지금 어떤 단계인가"를 봐야 하는 호출부가 있다.
// RentalStatus 는 reservation.api 에 있으므로 이 값을 넘겨도 경계를 넘지 않는다.
//
// 빌더를 두지 않는 이유는 auth/api/UserProfile 주석 참고.
@JvmRecord
data class RentalInfo(
    val rentalId: Long,
    val equipmentId: Long,
    val ownerIdSnapshot: Long,
    val renterId: Long,
    val productName: String,
    // rejectReason 은 거절된 대여에만 있다 — 계약상 null 허용.
    val rejectReason: String?,
    // status 가 null 이면 결제 가능 여부·상태 전이 판정이 조용히 뒤집힌다.
    val status: RentalStatus,
    val totalPrice: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    fun isRenter(userId: Long): Boolean = renterId == userId
    fun isOwner(userId: Long): Boolean = ownerIdSnapshot == userId
}
