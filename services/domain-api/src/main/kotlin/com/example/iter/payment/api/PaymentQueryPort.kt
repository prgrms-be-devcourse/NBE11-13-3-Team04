package com.example.iter.payment.api

import java.util.Optional

// payment 가 다른 도메인에게 공개하는 결제 조회 창구.
// 규약은 auth/api/UserQueryPort 의 주석을 따른다.
interface PaymentQueryPort {

    // 전체 결제 건수. 상태로 거르지 않는다 (취소·환불 포함) —
    // 기존 PaymentRepository.count() 와 동작이 같아야 한다.
    fun count(): Long

    // 이 대여의 결제가 환불되었는가. 결제 기록 자체가 없으면 false.
    //
    // PaymentStatus 를 돌려주지 않는 이유는, 호출부(알림 리스너)가 알고 싶은 것이
    // "환불 안내 문구를 넣을지 말지" 하나뿐이기 때문이다. 상태를 넘기면
    // 결제 상태가 늘어날 때마다 알림 코드도 같이 봐야 한다.
    //
    // rentalId 는 nullable 로 선언한다 — 구현체(JpaPaymentQueryAdapter)가 이번 라운드에서는
    // 아직 자바라 Long 파라미터가 boxed 참조형이다. 코틀린 non-null Long 은 바이트코드에서
    // primitive long 으로 컴파일돼 자바 구현체의 override 시그니처가 깨진다.
    fun isRefundedForRental(rentalId: Long?): Boolean

    // 이 대여의 결제 상태. 결제 기록이 없으면 빈 값.
    fun findStatusByRentalId(rentalId: Long?): Optional<PaymentStatus>

    // 여러 대여의 결제 상태를 한 번에. 결제 기록이 없는 대여는 결과 Map 에서 빠진다.
    // !! 한 건씩 루프로 호출하지 말 것 !! 목록 화면에서 N+1 이 된다.
    fun findStatusesByRentalIds(rentalIds: Collection<Long>): Map<Long, PaymentStatus>
}
