package iter.reservation.domain.policy

import iter.reservation.api.RentalStatus

// 정적 유틸 클래스였다. 자바 호출부(RentalStatusPolicy.xxxStatuses())가 그대로 컴파일되려면
// object + @JvmStatic 이 필요하다.
//
// 집합을 전부 java.util.Set.of/copyOf 로 만든다 — kotlin.collections.setOf()나 Set 의
// 뺄셈 연산자(-)는 컴파일 타임에만 읽기 전용이고 실제 런타임 객체는 LinkedHashSet 이라
// 자바 호출부가 .remove() 를 부르면 조용히 성공해 버린다(예외가 안 난다). 원본은
// Set.of/Set.copyOf 로 진짜 불변 집합을 돌려주고 있었고, RentalStatusPolicyTest 가
// withdrawalBlockingStatuses().remove(...) 에서 UnsupportedOperationException 을
// 기대하므로 이 불변성을 그대로 지켜야 한다.
object RentalStatusPolicy {

    private val TERMINAL_STATUSES: Set<RentalStatus> = java.util.Set.of(
        RentalStatus.COMPLETED,
        RentalStatus.REJECTED,
        RentalStatus.CANCELED,
    )

    private val WITHDRAWAL_BLOCKING_STATUSES: Set<RentalStatus> = java.util.Set.copyOf(
        java.util.EnumSet.complementOf(
            java.util.EnumSet.of(
                RentalStatus.COMPLETED,
                RentalStatus.REJECTED,
                RentalStatus.CANCELED,
            )
        )
    )

    // DISPUTED는 서비스에서 ACTIVE_DISPUTE_EXISTS로 별도 차단합니다.
    private val EQUIPMENT_DELETION_BLOCKING_STATUSES: Set<RentalStatus> = java.util.Set.copyOf(
        java.util.EnumSet.complementOf(
            java.util.EnumSet.of(
                RentalStatus.COMPLETED,
                RentalStatus.REJECTED,
                RentalStatus.CANCELED,
                RentalStatus.DISPUTED,
            )
        )
    )

    // 관리자 회원 상세의 "거래 성사" 집계 기준. 이전에는 auth 가 EnumSet 상수로 들고 있었다.
    private val ESTABLISHED_STATUSES: Set<RentalStatus> = java.util.Set.copyOf(
        java.util.EnumSet.of(
            RentalStatus.APPROVED, // 장비 등록자가 대여 요청을 승인한 상태
            RentalStatus.SHIPPING, // 장비를 대여자에게 배송 중인 상태
            RentalStatus.RECEIVED, // 대여자가 장비 수령을 확인한 상태
            RentalStatus.RENTING, // 장비를 실제로 대여 중인 상태
            RentalStatus.RETURN_REQUESTED, // 대여자가 반납을 신청한 상태
            RentalStatus.RETURNING, // 장비를 등록자에게 반송 중인 상태
            RentalStatus.RETURNED, // 반송이 완료되어 반납 확인 단계인 상태
            RentalStatus.DISPUTED, // 반납 과정에서 문제가 발생해 분쟁 중인 상태
            RentalStatus.COMPLETED, // 반납 확인까지 끝나 거래가 최종 완료된 상태
        )
    )

    // 종료일이 지났을 때 연체로 집계할 상태. 장비가 아직 등록자에게 돌아가지 않은 것만 포함한다.
    private val OVERDUE_STATUSES: Set<RentalStatus> = java.util.Set.copyOf(
        java.util.EnumSet.of(
            RentalStatus.RECEIVED, // 대여자가 장비를 수령했고 아직 반환하지 않은 상태
            RentalStatus.RENTING, // 장비를 실제로 대여 중인 상태
            RentalStatus.RETURN_REQUESTED, // 반납을 신청했지만 아직 반송하지 않은 상태
            RentalStatus.RETURNING, // 반송 중이지만 등록자에게 아직 도착하지 않은 상태
        )
    )

    @JvmStatic
    fun establishedStatuses(): Set<RentalStatus> = ESTABLISHED_STATUSES

    @JvmStatic
    fun overdueStatuses(): Set<RentalStatus> = OVERDUE_STATUSES

    @JvmStatic
    fun terminalStatuses(): Set<RentalStatus> = TERMINAL_STATUSES

    @JvmStatic
    fun withdrawalBlockingStatuses(): Set<RentalStatus> = WITHDRAWAL_BLOCKING_STATUSES

    @JvmStatic
    fun equipmentDeletionBlockingStatuses(): Set<RentalStatus> = EQUIPMENT_DELETION_BLOCKING_STATUSES
}
