package com.example.iter.reservation.domain.entity

import com.example.iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

import java.time.LocalDateTime

// ERD RECEIPT 엔티티 — 수령 시점 상태 기록 (Rental과 같은 도메인이므로 직접 연관관계 사용)
@Entity
@Table(name = "receipt")
class Receipt @JvmOverloads constructor(

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id", nullable = false, unique = true)
    val rental: Rental,

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false, length = 20)
    val productCondition: ProductConditionType,

    @Column(name = "condition_detail", columnDefinition = "TEXT")
    val conditionDetail: String? = null,

    @Column(name = "received_at")
    val receivedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseCreatedAtEntity()
