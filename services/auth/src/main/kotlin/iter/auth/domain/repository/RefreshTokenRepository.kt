package iter.auth.domain.repository

import iter.auth.domain.entity.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByTokenHash(tokenHash: String): Optional<RefreshToken>

    fun findAllByUserId(userId: Long): List<RefreshToken>

    fun findAllByFamilyId(familyId: String): List<RefreshToken>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE RefreshToken r
        SET r.revokedAt = :revokedAt
        WHERE r.familyId = :familyId
          AND r.revokedAt IS NULL
        """,
    )
    fun revokeAllActiveByFamilyId(
        @Param("familyId") familyId: String,
        @Param("revokedAt") revokedAt: LocalDateTime,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE RefreshToken r
        SET r.revokedAt = :revokedAt
        WHERE r.userId = :userId
          AND r.revokedAt IS NULL
        """,
    )
    fun revokeAllActiveByUserId(
        @Param("userId") userId: Long,
        @Param("revokedAt") revokedAt: LocalDateTime,
    ): Int
}
