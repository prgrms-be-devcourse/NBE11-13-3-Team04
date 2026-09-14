package com.example.iter.payment.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// payment 가 다른 도메인에게 공개하는 결제 조회 창구.
// 규약은 auth/api/UserQueryPort 의 주석을 따른다.
public interface PaymentQueryPort {

    // 전체 결제 건수. 상태로 거르지 않는다 (취소·환불 포함) —
    // 기존 PaymentRepository.count() 와 동작이 같아야 한다.
    long count();

    // 이 대여의 결제가 환불되었는가. 결제 기록 자체가 없으면 false.
    //
    // PaymentStatus 를 돌려주지 않는 이유는, 호출부(알림 리스너)가 알고 싶은 것이
    // "환불 안내 문구를 넣을지 말지" 하나뿐이기 때문이다. 상태를 넘기면
    // 결제 상태가 늘어날 때마다 알림 코드도 같이 봐야 한다.
    boolean isRefundedForRental(Long rentalId);

    // 이 대여의 결제 상태. 결제 기록이 없으면 빈 값.
    Optional<PaymentStatus> findStatusByRentalId(Long rentalId);

    // 여러 대여의 결제 상태를 한 번에. 결제 기록이 없는 대여는 결과 Map 에서 빠진다.
    // !! 한 건씩 루프로 호출하지 말 것 !! 목록 화면에서 N+1 이 된다.
    Map<Long, PaymentStatus> findStatusesByRentalIds(Collection<Long> rentalIds);
}
