package com.example.iter.auth.domain.entity

import com.example.iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "user_address",
    indexes = [Index(name = "idx_user_address_user_default", columnList = "user_id, is_default")],
)
class UserAddress @JvmOverloads constructor(
    userId: Long,
    recipientName: String,
    recipientPhone: String,
    zipcode: String,
    address: String,
    detailAddress: String,
    defaultAddress: Boolean = true,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseCreatedAtEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "recipient_name", nullable = false, length = 20)
    var recipientName: String = recipientName
        protected set

    @Column(name = "recipient_phone", nullable = false, length = 20)
    var recipientPhone: String = recipientPhone
        protected set

    @Column(nullable = false, length = 10)
    var zipcode: String = zipcode
        protected set

    @Column(nullable = false, length = 200)
    var address: String = address
        protected set

    @Column(name = "detail_address", nullable = false, length = 200)
    var detailAddress: String = detailAddress
        protected set

    // 프로퍼티 이름은 defaultAddress로 유지한다 — Spring Data JPA가
    // findByUserIdAndDefaultAddressTrue 같은 파생 쿼리에서 엔티티 프로퍼티를 이 이름으로
    // 찾는다(@Entity라 open이라 @get:JvmName으로 게터 이름만 바꾸는 것도 안 된다).
    // 자바 호출부는 Lombok이 예전에 만들어주던 isDefaultAddress()를 그대로 쓰므로,
    // 코틀린이 자동 생성하는 getDefaultAddress()와 별개로 같은 값을 돌려주는
    // isDefaultAddress()를 하나 더 둔다.
    @Column(name = "is_default", nullable = false)
    var defaultAddress: Boolean = defaultAddress
        protected set

    fun isDefaultAddress(): Boolean = defaultAddress

    fun update(
        recipientName: String,
        recipientPhone: String,
        zipcode: String,
        address: String,
        detailAddress: String,
    ) {
        this.recipientName = recipientName
        this.recipientPhone = recipientPhone
        this.zipcode = zipcode
        this.address = address
        this.detailAddress = detailAddress
    }

    class Builder {
        private var userId: Long? = null
        private var recipientName: String? = null
        private var recipientPhone: String? = null
        private var zipcode: String? = null
        private var address: String? = null
        private var detailAddress: String? = null
        private var defaultAddress: Boolean = true
        private var id: Long? = null

        fun id(id: Long?) = apply { this.id = id }
        fun userId(userId: Long) = apply { this.userId = userId }
        fun recipientName(recipientName: String) = apply { this.recipientName = recipientName }
        fun recipientPhone(recipientPhone: String) = apply { this.recipientPhone = recipientPhone }
        fun zipcode(zipcode: String) = apply { this.zipcode = zipcode }
        fun address(address: String) = apply { this.address = address }
        fun detailAddress(detailAddress: String) = apply { this.detailAddress = detailAddress }
        fun defaultAddress(defaultAddress: Boolean) = apply { this.defaultAddress = defaultAddress }

        fun build(): UserAddress = UserAddress(
            userId = requireNotNull(userId) { "userId" },
            recipientName = recipientName ?: "",
            recipientPhone = recipientPhone ?: "",
            zipcode = zipcode ?: "",
            address = address ?: "",
            detailAddress = detailAddress ?: "",
            defaultAddress = defaultAddress,
            id = id,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
