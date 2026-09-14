package com.example.iter.reservation.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// reservation 이 다른 도메인에게 공개하는 대여 조회 창구.
// 규약은 auth/api/UserQueryPort 의 주석을 따른다.
//
// !! 메서드가 RentalStatus 컬렉션을 인자로 받지 않는다 !!
// "어떤 상태가 탈퇴를 막는가", "어떤 상태가 장비 삭제를 막는가" 는 reservation 의 지식이다.
// 상태 집합을 인자로 받으면 호출부(auth, device)가 그 정책을 알아야 하고,
// 상태가 하나 늘어날 때마다 호출부도 같이 고쳐야 한다.
// 질문을 넘기고 판단은 여기 남긴다.
public interface RentalQueryPort {

    // 대여 한 건. 상태로 거르지 않는다 (취소·완료 건도 그대로 돌려준다).
    Optional<RentalInfo> find(Long rentalId);

    // 여러 건을 한 번에. 못 찾은 ID 는 결과 Map 에서 빠진다.
    // !! 한 건씩 루프로 호출하지 말 것 !! N+1 이 되고 목킹한 테스트는 통과한다.
    Map<Long, RentalInfo> findAll(Collection<Long> rentalIds);

    // 이 회원의 탈퇴를 막는 대여가 있는가. 빌린 것과 빌려준 것 양쪽을 본다.
    boolean hasWithdrawalBlockingRental(Long userId);

    // 관리자 회원 상세에 표시할 거래 건수.
    // 호출 세 번을 한 번으로 묶는다 — 호출부가 상태 집합 상수를 들고 있을 이유가 없다.
    UserRentalStats countUserRentalStats(Long userId, LocalDate today);

    // 이 장비에 분쟁 중인 대여가 있는가.
    boolean hasDisputedRental(Long equipmentId);

    // 이 장비의 삭제를 막는 대여가 있는가.
    boolean hasDeletionBlockingRental(Long equipmentId);

    // 이 기간에 겹치는, 장비를 점유하는 대여가 있는가.
    boolean hasConflictingOccupyingRental(Long equipmentId, LocalDate from, LocalDate to);

    // 이 기간 "밖"에 장비를 점유하는 대여가 있는가.
    // 장비의 대여 가능 기간을 줄일 때, 이미 잡힌 예약이 새 기간을 벗어나는지 확인한다.
    boolean hasOccupyingRentalOutsidePeriod(Long equipmentId, LocalDate from, LocalDate to);

    // 장비의 예약 일정. 취소·거절처럼 일정에 표시하지 않는 상태는 reservation 이 걸러서 준다.
    List<RentalScheduleItem> findSchedule(Long equipmentId, LocalDate from, LocalDate to);
}
