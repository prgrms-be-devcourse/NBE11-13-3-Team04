package com.example.iter.device.api

import java.util.Optional

// device 가 공개하는 장비 행 잠금 창구.
//
// EquipmentQueryPort 와 타입을 나눈 이유는 auth/api/UserLockPort 주석과 같다 —
// find 와 lock 은 반환 타입이 같아서 한 인터페이스에 있으면 바꿔 써도 컴파일된다.
//
// !! 서비스가 분리돼도 이 포트만은 REST 로 바꿀 수 없다 !!
// 행 락은 트랜잭션에 묶여 있어 네트워크 너머로 전달되지 않는다.
interface EquipmentLockPort {

    // 장비 행을 잠그고 잠근 시점의 상태를 돌려준다.
    //
    // 대여 생성은 선점(first-come) 방식이라 "겹치는지 확인"과 "저장"이
    // 하나의 원자적 구간이어야 한다. 이 락이 그 구간을 직렬화한다.
    // 락 없이 조회로 대체하면 두 명이 같은 기간을 동시에 통과한다 —
    // 컴파일도 되고 단위 테스트도 통과하며, RentalCreationConcurrencyTest 만 잡는다.
    fun lockForUpdate(equipmentId: Long): Optional<EquipmentInfo>
}
