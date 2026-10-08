package iter.auth.domain.entity

import iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "oauth_pending_token",
    indexes = [
        Index(name = "idx_oauth_pending_token_expires_at", columnList = "expires_at"),
        Index(name = "idx_oauth_pending_token_provider_user", columnList = "provider, provider_user_id"),
    ],
)
class OAuthPendingToken @JvmOverloads constructor(
    tokenHash: String,
    provider: OAuthProvider,
    providerUserId: String,
    purpose: OAuthPendingPurpose,
    expiresAt: LocalDateTime,
    email: String? = null,
    nickname: String? = null,
    targetUserId: Long? = null,
    usedAt: LocalDateTime? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Version
    val version: Long? = null,
) : BaseCreatedAtEntity() {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String = tokenHash
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: OAuthProvider = provider
        protected set

    @Column(name = "provider_user_id", nullable = false, length = 100)
    var providerUserId: String = providerUserId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var purpose: OAuthPendingPurpose = purpose
        protected set

    @Column(length = 100)
    var email: String? = email
        protected set

    @Column(length = 20)
    var nickname: String? = nickname
        protected set

    @Column(name = "target_user_id")
    var targetUserId: Long? = targetUserId
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = expiresAt
        protected set

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = usedAt
        protected set

    fun isExpired(now: LocalDateTime): Boolean = !expiresAt.isAfter(now)

    fun isUsed(): Boolean = usedAt != null

    fun use(usedAt: LocalDateTime) {
        check(!isUsed()) { "이미 사용한 OAuth 일회용 토큰입니다." }
        this.usedAt = usedAt
        email = null
        nickname = null
    }

    class Builder {
        private var tokenHash: String? = null
        private var provider: OAuthProvider? = null
        private var providerUserId: String? = null
        private var purpose: OAuthPendingPurpose? = null
        private var email: String? = null
        private var nickname: String? = null
        private var targetUserId: Long? = null
        private var expiresAt: LocalDateTime? = null
        private var usedAt: LocalDateTime? = null
        private var id: Long? = null
        private var version: Long? = null

        fun id(id: Long?) = apply { this.id = id }
        fun version(version: Long?) = apply { this.version = version }
        fun tokenHash(tokenHash: String) = apply { this.tokenHash = tokenHash }
        fun provider(provider: OAuthProvider) = apply { this.provider = provider }
        fun providerUserId(providerUserId: String) = apply { this.providerUserId = providerUserId }
        fun purpose(purpose: OAuthPendingPurpose) = apply { this.purpose = purpose }
        fun email(email: String?) = apply { this.email = email }
        fun nickname(nickname: String?) = apply { this.nickname = nickname }
        fun targetUserId(targetUserId: Long?) = apply { this.targetUserId = targetUserId }
        fun expiresAt(expiresAt: LocalDateTime) = apply { this.expiresAt = expiresAt }
        fun usedAt(usedAt: LocalDateTime?) = apply { this.usedAt = usedAt }

        fun build(): OAuthPendingToken = OAuthPendingToken(
            tokenHash = tokenHash ?: "",
            provider = requireNotNull(provider) { "provider" },
            providerUserId = providerUserId ?: "",
            purpose = requireNotNull(purpose) { "purpose" },
            email = email,
            nickname = nickname,
            targetUserId = targetUserId,
            expiresAt = requireNotNull(expiresAt) { "expiresAt" },
            usedAt = usedAt,
            id = id,
            version = version,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
