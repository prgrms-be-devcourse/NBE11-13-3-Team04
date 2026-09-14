package com.example.iter.reservation.api;

import java.util.Optional;

// reservation 이 다른 도메인에게 공개하는 대여 변경 창구.
public interface RentalCommandPort {

    // 결제가 확정되어 대여를 "요청됨" 단계로 올린다.
    //
    // !! 반드시 변경 "후" 상태를 돌려준다 !!
    // void 로 두면 호출부가 변경 전에 조회해둔 값을 응답에 실어
    // rentalStatus 가 REQUESTED 가 아니라 PENDING 으로 나간다.
    // 포트를 목킹한 단위 테스트는 스텁 값을 그대로 돌려주므로 이걸 잡지 못한다.
    //
    // !! 대여가 없으면 예외가 아니라 빈 값이다 !!
    // 토스 웹훅은 대여가 없어도 조용히 넘어가야 한다. 예외를 던지면 500 이 나가고
    // 토스가 재시도 루프에 들어간다. 결제 승인 경로는 빈 값을 받아 스스로 예외를 고른다 —
    // 같은 "못 찾음"에 대한 처리가 호출 경로마다 다르다는 AuthUserLoader 의 원칙과 같다.
    Optional<RentalInfo> markPaymentConfirmed(Long rentalId);
}
