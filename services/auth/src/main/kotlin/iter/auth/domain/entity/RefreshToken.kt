package iter.auth.domain.entity

import iter.common.entity.BaseCreatedAtEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "refresh_token",
    indexes = [
        Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
        Index(name = "idx_refresh_token_family_id", columnList = "family_id"),
        Index(name = "idx_refresh_token_expires_at", columnList = "expires_at"),
    ],
)
class RefreshToken @JvmOverloads constructor(
    userId: Long,
    tokenHash: String,
    familyId: String,
    expiresAt: LocalDateTime,
    revokedAt: LocalDateTime? = null,
    replacedByTokenId: Long? = null,
    lastUsedAt: LocalDateTime? = null,
    deviceInfo: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Version
    val version: Long? = null,
) : BaseCreatedAtEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String = tokenHash
        protected set

    @Column(name = "family_id", nullable = false, length = 36)
    var familyId: String = familyId
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = expiresAt
        protected set

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = revokedAt
        protected set

    @Column(name = "replaced_by_token_id")
    var replacedByTokenId: Long? = replacedByTokenId
        protected set

    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = lastUsedAt
        protected set

    @Column(name = "device_info", length = 255)
    var deviceInfo: String? = deviceInfo
        protected set

    fun isExpired(now: LocalDateTime): Boolean = !expiresAt.isAfter(now)

    fun isRevoked(): Boolean = revokedAt != null

    fun isActive(now: LocalDateTime): Boolean = !isRevoked() && !isExpired(now)

    fun markUsed(usedAt: LocalDateTime) {
        lastUsedAt = usedAt
    }

    fun revoke(revokedAt: LocalDateTime) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt
        }
    }

    fun rotateTo(replacementTokenId: Long?, usedAt: LocalDateTime) {
        requireNotNull(replacementTokenId) { "교체 토큰 ID는 필수입니다." }
        check(!isRevoked()) { "이미 폐기된 Refresh Token입니다." }

        lastUsedAt = usedAt
        revokedAt = usedAt
        replacedByTokenId = replacementTokenId
    }

    class Builder {
        private var userId: Long? = null
        private var tokenHash: String? = null
        private var familyId: String? = null
        private var expiresAt: LocalDateTime? = null
        private var revokedAt: LocalDateTime? = null
        private var replacedByTokenId: Long? = null
        private var lastUsedAt: LocalDateTime? = null
        private var deviceInfo: String? = null
        private var id: Long? = null
        private var version: Long? = null

        fun id(id: Long?) = apply { this.id = id }
        fun version(version: Long?) = apply { this.version = version }
        fun userId(userId: Long) = apply { this.userId = userId }
        fun tokenHash(tokenHash: String) = apply { this.tokenHash = tokenHash }
        fun familyId(familyId: String) = apply { this.familyId = familyId }
        fun expiresAt(expiresAt: LocalDateTime) = apply { this.expiresAt = expiresAt }
        fun revokedAt(revokedAt: LocalDateTime?) = apply { this.revokedAt = revokedAt }
        fun replacedByTokenId(replacedByTokenId: Long?) = apply { this.replacedByTokenId = replacedByTokenId }
        fun lastUsedAt(lastUsedAt: LocalDateTime?) = apply { this.lastUsedAt = lastUsedAt }
        fun deviceInfo(deviceInfo: String?) = apply { this.deviceInfo = deviceInfo }

        fun build(): RefreshToken = RefreshToken(
            userId = requireNotNull(userId) { "userId" },
            tokenHash = tokenHash ?: "",
            familyId = familyId ?: "",
            expiresAt = requireNotNull(expiresAt) { "expiresAt" },
            revokedAt = revokedAt,
            replacedByTokenId = replacedByTokenId,
            lastUsedAt = lastUsedAt,
            deviceInfo = deviceInfo,
            id = id,
            version = version,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
