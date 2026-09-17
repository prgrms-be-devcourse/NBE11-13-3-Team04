package com.example.iter.common.entity

import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

// created_at 컬럼만 갖는 엔티티(EquipmentImage, Receipt, DisputeImage 등)가 상속하는 공통 클래스.
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseCreatedAtEntity {

    @CreatedDate
    var createdAt: LocalDateTime? = null
        protected set
}
