package com.example.iter.device.domain.entity

import com.example.iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

// 리뷰 기능은 현재 비활성화되어 애플리케이션 조회에서 사용하지 않습니다.
// 기존 DB 스키마 검증과 향후 기능 복구 가능성을 위해 엔티티 매핑만 유지합니다.
// rentalId, userId는 각각 reservation/auth 도메인의 PK를 값으로만 참조합니다.
@Entity
@Table(name = "review")
class Review @JvmOverloads constructor(

    @Column(name = "rental_id", nullable = false, unique = true)
    val rentalId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    val equipment: Equipment,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val rating: Int,

    @Column(columnDefinition = "TEXT")
    val content: String?,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseTimeEntity()
