package com.example.iter.device.domain.entity

import com.example.iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import java.time.LocalDateTime

@Entity
@Table(name = "equipment_image_upload")
class EquipmentImageUpload @JvmOverloads constructor(

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    val objectKey: String,

    @Column(name = "expected_content_type", nullable = false, length = 50)
    val expectedContentType: String,

    @Column(name = "expected_size", nullable = false)
    val expectedSize: Long,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    // id 를 맨 뒤에 두는 이유는 EquipmentOccupancy 와 같다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

) : BaseCreatedAtEntity() {

    // 자바에서는 @Getter 만 있고 @Setter 가 없어 바깥에서 못 바꾸고 use() 로만 채웠다.
    // private set 은 못 쓴다 — plugin.jpa 가 엔티티를 open 으로 열어주는데 코틀린은
    // open 프로퍼티의 private setter 를 금지한다(지연 로딩 프록시가 상속을 쓰기 때문).
    // protected 면 같은 효과다: 바깥 패키지에서는 못 부르고 프록시만 접근한다.
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null
        protected set

    fun isExpired(now: LocalDateTime): Boolean = !expiresAt.isAfter(now)

    fun isUsed(): Boolean = usedAt != null

    fun use(usedAt: LocalDateTime) {
        check(!isUsed()) { "이미 사용한 장비 이미지 업로드입니다." }
        this.usedAt = usedAt
    }
}
