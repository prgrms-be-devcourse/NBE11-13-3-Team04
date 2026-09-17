package com.example.iter.device.api

import java.time.LocalDateTime

// device 가 공개하는 장비 변경 창구.
interface EquipmentCommandPort {

    // 이 회원이 등록한 장비를 전부 삭제 상태로 바꾼다. 변경된 건수를 돌려준다.
    //
    // 회원 탈퇴 처리에서 호출한다. 어떤 상태가 "삭제"인지는 device 가 정하므로
    // 호출부가 EquipmentStatus 를 알 필요가 없다.
    //
    // !! 구현에서 @Modifying 플래그를 바꾸지 말 것 !!
    // 현재 flushAutomatically = true 이고 clearAutomatically 는 없다.
    // clearAutomatically 를 추가하면 호출자가 락으로 들고 있던 User 가 detach 되어
    // 탈퇴 처리의 user.withdraw() 가 아무것도 저장하지 않는다.
    // 장비는 삭제되는데 회원 상태만 그대로 남고, 포트를 목킹한 단위 테스트는 전부 통과한다.
    fun deactivateAllOwnedBy(ownerId: Long, deactivatedAt: LocalDateTime): Int
}
