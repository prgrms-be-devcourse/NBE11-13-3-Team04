package com.example.iter.reservation.api;

// 관리자 회원 상세에 표시하는 거래 건수.
//
// 이전에는 auth 가 ESTABLISHED_STATUSES / OVERDUE_STATUSES 라는 EnumSet 상수를 직접 들고
// 리포지토리를 세 번 호출했다. "성사된 거래란 무엇인가"는 reservation 의 정의다.
public record UserRentalStats(
        long rentedCount,
        long lentCount,
        long overdueCount
) {
}
