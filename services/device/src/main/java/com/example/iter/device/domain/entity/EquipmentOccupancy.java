package com.example.iter.device.domain.entity;

import com.example.iter.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 공개 장비 검색의 가용성 판정 전용 프로젝션. reservation이 예약 생성/취소/거절/반납완료
// 시점에 갱신한다(RentalConflictPolicy.nonOccupyingStatuses에 들면 행이 삭제된다).
// 최종 일관성 — 예약 생성 시점의 비관적 락 재검증(findConflictingOccupyingRentalsForUpdate)이
// 실제 오버부킹을 막으므로, 이 테이블이 잠깐 낡아도 안전하다(검색 필터는 조언적일 뿐).
@Entity
@Table(
        name = "equipment_occupied_rental",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_equipment_occupied_rental_rental_id",
                columnNames = "rental_id"
        ),
        indexes = {
                @Index(
                        name = "idx_equipment_occupied_rental_equipment_period",
                        columnList = "equipment_id, start_date, end_date"
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EquipmentOccupancy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "rental_id", nullable = false)
    private Long rentalId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}
