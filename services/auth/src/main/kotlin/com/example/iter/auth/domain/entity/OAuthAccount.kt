package com.example.iter.auth.domain.entity

import com.example.iter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "oauth_account",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_oauth_account_provider_user", columnNames = ["provider", "provider_user_id"]),
        UniqueConstraint(name = "uk_oauth_account_user_provider", columnNames = ["user_id", "provider"]),
    ],
)
class OAuthAccount @JvmOverloads constructor(
    userId: Long,
    provider: OAuthProvider,
    providerUserId: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : BaseTimeEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: OAuthProvider = provider
        protected set

    @Column(name = "provider_user_id", nullable = false, length = 100)
    var providerUserId: String = providerUserId
        protected set

    class Builder {
        private var userId: Long? = null
        private var provider: OAuthProvider? = null
        private var providerUserId: String? = null
        private var id: Long? = null

        fun id(id: Long?) = apply { this.id = id }
        fun userId(userId: Long) = apply { this.userId = userId }
        fun provider(provider: OAuthProvider) = apply { this.provider = provider }
        fun providerUserId(providerUserId: String) = apply { this.providerUserId = providerUserId }

        fun build(): OAuthAccount = OAuthAccount(
            userId = requireNotNull(userId) { "userId" },
            provider = requireNotNull(provider) { "provider" },
            providerUserId = providerUserId ?: "",
            id = id,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
