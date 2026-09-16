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

import java.time.LocalDate

// ERD RETURN_RECEIPT 엔티티 — 반납 시점 상태 기록. Receipt(수령 시점)와 비교해 분쟁 자동 감지에 사용됨 (기획서 6-2 참고)
@Entity
@Table(name = "return_receipt")
class ReturnReceipt @JvmOverloads constructor(

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id", nullable = false, unique = true)
    val rental: Rental,

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false, length = 20)
    val productCondition: ProductConditionType,

    @Column(name = "condition_detail", columnDefinition = "TEXT")
    val conditionDetail: String? = null,

    @Column(name = "return_date")
    val returnDate: LocalDate? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseCreatedAtEntity()
