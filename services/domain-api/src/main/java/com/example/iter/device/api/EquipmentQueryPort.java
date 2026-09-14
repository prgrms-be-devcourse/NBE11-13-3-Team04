package com.example.iter.device.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// device 가 다른 도메인에게 공개하는 장비 조회 창구.
// 규약은 auth/api/UserQueryPort 의 주석을 따른다.
public interface EquipmentQueryPort {

    // 전체 장비 수. 삭제된 장비를 제외하지 않는다 —
    // 기존 EquipmentRepository.count() 와 동작이 같아야 한다.
    long count();

    // 장비 소유자의 회원 ID. 장비가 없으면 빈 값.
    // 소유자 ID 하나만 필요한 호출부(알림 리스너)를 위해 남겨둔다.
    Optional<Long> findOwnerId(Long equipmentId);

    // 장비 한 건. 상태로 거르지 않는다 (삭제된 장비도 그대로 돌려준다) —
    // "삭제됐으면 거절"은 호출부의 정책이고 에러 코드도 호출부마다 다르다.
    Optional<EquipmentInfo> find(Long equipmentId);

    // 여러 건을 한 번에. 못 찾은 ID 는 결과 Map 에서 빠진다.
    // !! 한 건씩 루프로 호출하지 말 것 !! N+1 이 되고 목킹한 테스트는 통과한다.
    Map<Long, EquipmentInfo> findAll(Collection<Long> equipmentIds);
}
