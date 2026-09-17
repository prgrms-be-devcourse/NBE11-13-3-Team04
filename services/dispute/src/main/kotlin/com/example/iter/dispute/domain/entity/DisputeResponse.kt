package com.example.iter.dispute.domain.entity

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

// ERD DISPUTE_RESPONSE 엔티티 — 분쟁 당사자 간 의견(소명) 기록
// userId는 auth 도메인 PK를 값으로만 참조
@Entity
@Table(name = "dispute_response")
class DisputeResponse @JvmOverloads constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    val dispute: Dispute,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseTimeEntity()
