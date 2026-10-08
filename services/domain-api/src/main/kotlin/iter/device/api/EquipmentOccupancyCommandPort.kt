package iter.device.api

import java.time.LocalDate

// device가 소유한 예약 가용성 프로젝션(equipment_occupied_rental)을 reservation이 갱신하는 창구.
// 최종 일관성 — 검색 결과가 잠깐 낡을 수 있지만, 실제 예약 생성 시점의 비관적 락 재검증이
// 오버부킹을 막으므로 안전하다(searchPublicEquipment는 조언적 필터일 뿐).
interface EquipmentOccupancyCommandPort {

    // 새 대여가 점유를 시작한다.
    //
    // rentalId 는 nullable 로 선언한다 — RentalService.createRental 의 단위 테스트가
    // rentalRepository.save(any())를 그냥 echo-back 하는 목이라 저장된 Rental.id가
    // null인 채로 이 메서드에 전달된다(실제 JPA save는 즉시 ID를 채우지만 목은 그러지
    // 않는다). 자바 시그니처도 원래 boxed 참조형이라 null을 그대로 허용했다.
    fun markOccupied(rentalId: Long?, equipmentId: Long, startDate: LocalDate, endDate: LocalDate)

    // 대여 한 건이 점유를 끝낸다(취소·거절·반납완료).
    fun markVacated(rentalId: Long)

    // 여러 건이 한 번에 점유를 끝낸다(미결제 만료 배치).
    fun markVacatedAll(rentalIds: Collection<Long>)
}
