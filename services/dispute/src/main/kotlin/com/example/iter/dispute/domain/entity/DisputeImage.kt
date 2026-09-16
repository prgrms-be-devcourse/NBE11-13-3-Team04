package com.example.iter.dispute.domain.entity

import com.example.iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "dispute_image")
class DisputeImage @JvmOverloads constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    val dispute: Dispute,

    @Column(name = "image_url", nullable = false, length = 500)
    val imageUrl: String,

    @Column(name = "sort_order")
    val sortOrder: Int,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseCreatedAtEntity()
