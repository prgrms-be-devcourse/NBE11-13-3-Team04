package com.example.iter.reservation.api

import java.util.Optional

// reservation 이 다른 도메인에게 공개하는 후기 조회 창구.
//
// RentalQueryPort 와 나눈 이유는 대여와 후기가 서로 다른 애그리거트이기 때문이다.
// 한 포트에 몰면 나중에 후기만 별도 서비스로 떼어낼 때 인터페이스를 쪼개야 한다.
interface RentalReviewQueryPort {

    fun find(reviewId: Long): Optional<RentalReviewInfo>
}
