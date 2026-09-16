package com.example.iter.device.domain.entity

import com.example.iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

import java.time.LocalDate

// 공개 장비 검색의 가용성 판정 전용 프로젝션. reservation이 예약 생성/취소/거절/반납완료
// 시점에 갱신한다(RentalConflictPolicy.nonOccupyingStatuses에 들면 행이 삭제된다).
// 최종 일관성 — 예약 생성 시점의 비관적 락 재검증(findConflictingOccupyingRentalsForUpdate)이
// 실제 오버부킹을 막으므로, 이 테이블이 잠깐 낡아도 안전하다(검색 필터는 조언적일 뿐).
@Entity
@Table(
    name = "equipment_occupied_rental",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_equipment_occupied_rental_rental_id",
            columnNames = ["rental_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_equipment_occupied_rental_equipment_period",
            columnList = "equipment_id, start_date, end_date",
        ),
    ],
)
class EquipmentOccupancy @JvmOverloads constructor(

    @Column(name = "equipment_id", nullable = false)
    val equipmentId: Long,

    @Column(name = "rental_id", nullable = false)
    val rentalId: Long,

    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,

    // 자바 필드 선언에서는 맨 앞이었지만 생성자에서는 맨 뒤에 둔다 — @JvmOverloads 가
    // 뒤쪽 기본값부터 생략한 오버로드를 만들어주므로, 자바 호출부가 id 자리에 null 을
    // 넘기지 않아도 된다. 나머지 필드 순서는 자바 그대로 유지한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseTimeEntity()
