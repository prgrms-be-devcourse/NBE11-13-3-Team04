package com.example.iter.device.api;

import java.time.LocalDate;
import java.util.Collection;

// device가 소유한 예약 가용성 프로젝션(equipment_occupied_rental)을 reservation이 갱신하는 창구.
// 최종 일관성 — 검색 결과가 잠깐 낡을 수 있지만, 실제 예약 생성 시점의 비관적 락 재검증이
// 오버부킹을 막으므로 안전하다(searchPublicEquipment는 조언적 필터일 뿐).
public interface EquipmentOccupancyCommandPort {

    // 새 대여가 점유를 시작한다.
    void markOccupied(Long rentalId, Long equipmentId, LocalDate startDate, LocalDate endDate);

    // 대여 한 건이 점유를 끝낸다(취소·거절·반납완료).
    void markVacated(Long rentalId);

    // 여러 건이 한 번에 점유를 끝낸다(미결제 만료 배치).
    void markVacatedAll(Collection<Long> rentalIds);
}
