package com.example.iter.device.domain.entity

import com.example.iter.common.entity.BaseCreatedAtEntity
import com.example.iter.common.image.CaptureView
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// ERD EQUIPMENT_IMAGE 엔티티 — Equipment와 같은 도메인이므로 정상적인 JPA 연관관계를 사용
@Entity
@Table(
    name = "equipment_image",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_equipment_image_capture_view",
            columnNames = ["equipment_id", "capture_view"]
        )
    ]
)
class EquipmentImage @JvmOverloads constructor(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    val equipment: Equipment,

    @Column(name = "image_url", nullable = false, length = 500)
    val imageUrl: String,

    @Column(name = "object_key", length = 500)
    val objectKey: String?,

    sortOrder: Int,

    thumbnail: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_view", length = 16)
    val captureView: CaptureView? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

) : BaseCreatedAtEntity() {

    constructor(
        equipment: Equipment,
        imageUrl: String,
        objectKey: String?,
        sortOrder: Int,
        thumbnail: Boolean,
        id: Long?
    ) : this(equipment, imageUrl, objectKey, sortOrder, thumbnail, null, id)

    @Column(name = "sort_order")
    var sortOrder: Int = sortOrder
        protected set

    // 프로퍼티 이름을 thumbnail 로 유지한다. isThumbnail 로 두면 자바 호출부의 isThumbnail() 이
    // 그대로 살지만, JPA 속성 이름이 필드명을 따라 isThumbnail 이 되어 파생 쿼리
    // findByEquipment_IdInAndThumbnailTrueOrderBySortOrderAscIdAsc 가 런타임에 깨진다.
    // 대신 getter 가 isThumbnail() 에서 getThumbnail() 로 바뀌므로 호출부를 같이 고쳤다.
    @Column(name = "is_thumbnail")
    var thumbnail: Boolean = thumbnail
        protected set

    fun changeThumbnail(thumbnail: Boolean) {
        this.thumbnail = thumbnail
    }

    fun changeSortOrder(sortOrder: Int) {
        this.sortOrder = sortOrder
    }
}
