package com.example.iter.device.api

// device 가 공개하는 장비 썸네일 조회 창구.
//
// EquipmentQueryPort 와 나눈 이유: 썸네일은 장비 이미지라는 별도 애그리거트이고,
// 목록 화면에서만 필요해서 장비 조회와 수명주기가 다르다.
//
// 이 포트가 유일한 JPA 프록시 누수를 없앤다.
// 이전에는 reservation 이 EquipmentImage 를 직접 조회한 뒤
// image.getEquipment().getId() 로 device 엔티티의 LAZY 프록시를 초기화했다.
interface EquipmentThumbnailQueryPort {

    // 장비 ID -> 대표 이미지 URL. 썸네일이 없는 장비는 결과 Map 에서 빠진다.
    //
    // !! 일괄 조회를 한 건씩 쪼개지 말 것 !!
    // 목록 화면에서 호출되므로 N+1 이 바로 성능 문제가 된다.
    fun findThumbnailUrls(equipmentIds: Collection<Long>): Map<Long, String>
}
